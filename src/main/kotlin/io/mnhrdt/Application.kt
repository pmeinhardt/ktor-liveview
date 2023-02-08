package io.mnhrdt

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.mnhrdt.plugins.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSockets()
    configureSerialization()
    configureRouting()
}
