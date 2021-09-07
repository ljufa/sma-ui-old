package com.github.ljufa.toptweets

import com.sksamuel.hoplite.*


data class GrpcServer(val url: String,  val isSsl: Boolean)
data class Config(val grpcServer: GrpcServer)


val config = ConfigLoader.Builder()
    .addPropertySource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
    .addSource(PropertySource.resource("/application-test.yaml", optional = true))
    .addSource(PropertySource.resource("/application-local.yaml", optional = true))
    .addSource(PropertySource.resource("/application.yaml"))
    .build()
    .loadConfigOrThrow<Config>()

