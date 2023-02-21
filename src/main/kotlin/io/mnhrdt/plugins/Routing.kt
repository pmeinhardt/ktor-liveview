package io.mnhrdt.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.routing.*
import io.mnhrdt.plugins.live.*
import java.io.File
import java.util.*
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

const val src = "/assets/index.js"

@Serializable
@SerialName("IndexState")
class IndexState : LiveViewState() {
    var time: Date by property(Date())
}

class Index(state: IndexState) : LiveView<IndexState>(state) {
    companion object : LiveViewFactory<IndexState, Index>({ state -> Index(state) })

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
        html() {
            live["state"] = Json.encodeToString(state)
            head { title { +"Hello ${if (connected) "Browser" else "Ktor"}!" } }
            body {
                p { +"It is ${state.time}" }
                script(src = src) {}
            }
        }

    private fun now() = Date()
}

class CounterState(initial: Int) : LiveViewState() {
    var count: Int by property(initial)
}

class CounterOps(state: CounterState) : LiveOps() {
    val increment = fn("increment") { state.count += 1 }
}

class Counter(state: CounterState) : LiveView<CounterState>(state) {
    companion object : LiveViewFactory<CounterState, Counter>({ state -> Counter(state) })

    override val ops = CounterOps(state)

    override fun mount() {}

    override fun render(): String =
        html {
            head { title { "Count (connected=$connected)" } }
            body {
                p { +"Count = ${state.count}" }
                button {
                    type = ButtonType.button
                    live["click"] = ops.increment
                    +"Increment"
                }
                script(src = src) {}
            }
        }
}

val scope = LiveViewScope {
    endpoint = "/live"
}

fun Application.configureRouting() {
    install(AutoHeadResponse)

    routing {
        live(scope) {
            view("/", Index) {
                IndexState()
            }

            view("/counter", Counter) {
                val initial = parameters["initial"] ?: "0"
                CounterState(initial.toInt())
            }
        }

        static("/assets") {
            staticRootFolder = File("assets/build")
            files(".")
        }
    }
}
