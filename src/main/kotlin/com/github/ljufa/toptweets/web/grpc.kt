package com.github.ljufa.toptweets.web

import com.github.ljufa.toptweets.server.grpc.*
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.concurrent.TimeUnit


class TopTweetsGrpcClient : Closeable {
    private val channel: ManagedChannel = ManagedChannelBuilder.forTarget(config.grpcServer.url)
        .usePlaintext()
        .executor(Dispatchers.Default.asExecutor())
        .build()
    private val stub: TopTweetsGrpcKt.TopTweetsCoroutineStub =
        TopTweetsGrpcKt.TopTweetsCoroutineStub(channel)

    fun getTweets(request: TopTweetsRequest): TopTweetsResponse =
        runBlocking {
            stub.getTopTweets(request)
        }

    override fun close() {
        channel.shutdown().awaitTermination(15, TimeUnit.SECONDS)
    }

}

class TwitterApiGrpcClient : Closeable {
    private val channel: ManagedChannel = ManagedChannelBuilder.forTarget(config.grpcServer.url)
        .usePlaintext()
        .executor(Dispatchers.Default.asExecutor())
        .build()

    private val stub: TwitterApiGrpcKt.TwitterApiCoroutineStub = TwitterApiGrpcKt.TwitterApiCoroutineStub(channel)

    fun getRules(): MatchedRules = runBlocking {
        stub.getMatchedRules(Empty.getDefaultInstance())
    }

    fun getLanguages(daysFromNow: Int, rule: String): Languages = runBlocking {
        stub.getLanguages(ByRuleRequest.newBuilder().setDaysFromNow(daysFromNow).setRuleId(rule).build())
    }

    fun getHashTags(daysFromNow: Int): HashTags = runBlocking {
//        stub.getHashTags(ByRuleRequest.newBuilder().setDaysFromNow(daysFromNow).setRuleId("").build())
        HashTags.getDefaultInstance()
    }

    fun getUserMentions(daysFromNow: Int): UserMentions = runBlocking {
//        stub.getUserMentions(ByRuleRequest.newBuilder().setDaysFromNow(daysFromNow).setRuleId("").build())
        UserMentions.getDefaultInstance()
    }

    override fun close() {
        channel.shutdown().awaitTermination(15, TimeUnit.SECONDS)
    }

}
