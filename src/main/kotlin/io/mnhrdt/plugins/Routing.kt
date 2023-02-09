package io.mnhrdt.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.routing.*
import io.mnhrdt.plugins.live.*
import java.io.File
import java.util.Date
import kotlinx.html.*

class Index(private val connected: Boolean, private val name: String) : LiveView() {
    override fun render(): String =
        html {
            head { title { +"Hello $name" } }
            body {
                p { +"It is ${now()} ($connected)" }
                script(src = "/assets/index.js") {}
            }
        }

    private fun now() = Date()
}

val scope = LiveViewScope {
    endpoint = "/live"
}

fun Application.configureRouting() {
    install(AutoHeadResponse)

    routing {
        live(scope) {
            view("/") {
                val name = parameters["name"] ?: "Ktor"
                Index(connected, name)
            }
        }

        static("/assets") {
            staticRootFolder = File("assets/build")
            files(".")
        }
    }
}
