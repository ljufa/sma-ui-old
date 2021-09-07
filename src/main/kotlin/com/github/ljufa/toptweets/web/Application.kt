package com.github.ljufa.toptweets.web

import com.github.ljufa.toptweets.grpc.TopTweetsRequest
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.gson.gson
import io.ktor.html.respondHtml
import io.ktor.http.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.pingPeriod
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.timeout
import io.ktor.http.content.*
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.date.GMTDate
import io.ktor.websocket.webSocket
import kotlinx.css.CSSBuilder
import kotlinx.html.*
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val daysFromNow = 1


    install(ContentNegotiation) {
        gson {
        }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        header("MyCustomHeader")
        allowCredentials = true
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }

    install(CachingHeaders) {
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(
                    CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60),
                    expires = null as? GMTDate?
                )
                else -> null
            }
        }
    }

    install(DataConversion)

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        static("static") {
            resources("static")
        }
        get("/") {
            val tweetsGrpcClient = TopTweetsGrpcClient()
            val twitterApiGrpcClient = TwitterApiGrpcClient()
            val params = call.request.queryParameters
            val days = params["days"]?.toInt() ?: daysFromNow
            val devMode = params["dev"]?.toBoolean() ?: false
            val lang = params["lang"]
            val rules = params["rule"]
            val limit = params["limit"]?.toInt() ?: 9
            val reqBuilder = TopTweetsRequest.newBuilder().setLimit(limit).setDaysFromNow(days)
            lang?.split(",")?.forEach { reqBuilder.addIncludeLanguages(it) }
            rules?.split(",")?.forEach { reqBuilder.addIncludeRuleIds(it) }
            val languages =
                twitterApiGrpcClient.getLanguages(days, "").languageList.sortedByDescending { it.numberOfMatches }

            val hashTags =
                twitterApiGrpcClient.getHashTags(days).hashtagList
                    .filter { rules?.contains(it.ruleId) ?: true }
                    .filter { lang?.contains(it.lang) ?: true }
                    .sortedByDescending { it.numberOfMatches }.take(30)
            val userMentions =
                twitterApiGrpcClient.getUserMentions(days).userMentionList.filter { rules?.contains(it.ruleId) ?: true }
                    .filter {
                        lang?.contains(it.lang) ?: true
                    }
                    .sortedByDescending { it.numberOfMatches }.take(30)
            val ruleList = twitterApiGrpcClient.getRules().ruleList
            call.respondHtml {
                val ttl =
                    "${ruleList.firstOrNull { it.id == rules }?.tag ?: "All rules"} -  ${lang ?: "All languages"}"
                head {
                    title { +ttl }
                    styleLink("static/styles.css")
                    script(src = "https://platform.twitter.com/widgets.js") {}
                }
                body {
                    h1 { +ttl }
                    div(classes = "main-container") {
                        div(classes = "top") {
                            h2 { +"Hash tags (to exclude/include):" }
                            div(classes = "hashtag") {
                                hashTags.forEach { ht ->
                                    a(href = "#") {
                                        +" #${ht.tag}(${ht.numberOfMatches})"
                                    }
                                }
                            }
                            div(classes = "usermention") {
                                h2 { +"Users mentions (to exclude/include):" }
                                userMentions.forEach { mt ->
                                    a(href = "#") {
                                        +" @${mt.user}(${mt.numberOfMatches})"
                                    }
                                }
                            }
                        }
                        div(classes = "navigation") {
                            dl {
                                val url = "/?days=$days&limit=$limit&dev=$devMode"
                                dl { a(url) { +"All" } }
                                ruleList.sortedByDescending { it.numberOfMatches }
                                    .forEach { rule ->
                                        val ruleUrl = "$url&rule=${rule.id}"
                                        dt { a(ruleUrl) { +"${rule.tag} (${rule.numberOfMatches})" } }
                                        languages.take(10).forEach { lang ->
                                            dd {
                                                a("$ruleUrl&lang=${lang.id}") { +"------ ${lang.id} (${lang.numberOfMatches})" }
                                            }
                                        }
                                    }
                            }
                        }
                        div(classes = "wrapper") {
                            tweetsGrpcClient.getTweets(
                                reqBuilder.build()
                            ).statsList?.forEach {
                                div(classes = "box") {
                                    if (devMode) {
                                        a("https://twitter.com/web/status/${it.tweetId}") {
                                            +"${it.numberOfRefs} - ${it.tweetId}"
                                        }
                                    } else {
                                        blockQuote(classes = "twitter-tweet") {
                                            //attributes["data-theme"] = "dark"
                                            a("https://twitter.com/web/status/${it.tweetId}")
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            }
            tweetsGrpcClient.close()
            twitterApiGrpcClient.close()
        }

        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }

        install(StatusPages) {
            exception<AuthenticationException> { cause ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { cause ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }

        webSocket("/myws/echo") {
            send(Frame.Text("Hi from server"))
            while (true) {
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    send(Frame.Text("Client said: " + frame.readText()))
                }
            }
        }
    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()


fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}
