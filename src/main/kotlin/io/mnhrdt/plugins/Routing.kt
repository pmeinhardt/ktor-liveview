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

class IndexState : LiveViewState() {
    var time: Date by property(Date())
}

class Index(private val name: String) : LiveView() {
    override val state = IndexState()

    override fun mount() {
        if (connected) {
            launch {
                while (true) {
                    delay(1000L)
                    state.time = now()
                }
            }
        }
    }

    override fun render(): String =
        html {
            head { title { +"Hello $name (connected=$connected)" } }
            body {
                p { +"It is ${state.time}" }
                script(src = src) {}
            }
        }

    private fun now() = Date()
}

class CounterState : LiveViewState() {
    var count: Int by property(0)
}

class Counter(private val initial: Int) : LiveView() {
    override val state = CounterState()

    override fun mount() {
        state.count = initial
    }

    override fun render(): String =
        html {
            head { title { "count (connected=$connected)" } }
            body {
                p { +"Count = ${state.count}" }
                button {
                    type = ButtonType.button
                    live["click"] = "increment"
                    +"Increment"
                }
                script(src = src) {}
            }
        }

    // TODO: Hook up to button (method annotation?)
    private fun increment() {
        state.count += 1
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
