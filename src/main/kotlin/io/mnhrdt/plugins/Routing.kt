package io.mnhrdt.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.routing.*
import io.mnhrdt.plugins.live.*
import java.io.File
import java.util.Date
import kotlinx.coroutines.*
import kotlinx.html.*

const val src = "/assets/index.js"

class Index(private val name: String) : LiveView() {
    override fun mount() {
        if (connected) {
            launch {
                while (true) {
                    delay(1000L)
                    assign("time", now())
                }
            }
        }

        assign("time", now())
    }

    override fun render(): String =
        html {
            head { title { +"Hello $name (connected=$connected)" } }
            body {
                p { +"It is ${assigns["time"]}" }
                script(src = src) {}
            }
        }

    private fun now() = Date()
}

class Counter(private val initial: Int) : LiveView() {
    override fun mount() {
        assign("count", initial)
    }

    override fun render(): String =
        html {
            head { title { "count (connected=$connected)" } }
            body {
                p { +"Count = ${assigns["count"]}" }
                button { onClick = ""; +"Increment" }
                script(src = src) {}
            }
        }

    // TODO: Hook up to button (method annotation?)
    private fun increment() {
        assign("count", assigns["count"] as Int + 1)
    }
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
                Index(name)
            }

            view("/counter") {
                val initial = parameters["initial"] ?: "0"
                Counter(initial.toInt())
            }
        }

        static("/assets") {
            staticRootFolder = File("assets/build")
            files(".")
        }
    }
}
