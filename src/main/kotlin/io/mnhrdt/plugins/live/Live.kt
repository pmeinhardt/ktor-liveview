package io.mnhrdt.plugins.live

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlinx.serialization.*

class LiveViewScope private constructor(options: Options) {
    internal var installed = false

    val endpoint: String

    init {
        endpoint = options.endpoint
    }

    constructor(configure: Options.() -> Unit) : this(Options().apply(configure))

    class Options {
        var endpoint: String = "/live"
    }
}

class LiveViewContext(val connected: Boolean, val parameters: Parameters)

@Serializable
data class LiveHello(val path: String, val parameters: Map<String, String>)

class LiveRouting(private val route: Route, private val scope: LiveViewScope) {
    private val handlers = mutableMapOf<String, LiveViewContext.() -> LiveView>()

    init {
        if (!scope.installed) {
            route.webSocket(scope.endpoint) {
                val hello = receiveDeserialized<LiveHello>()
                val init = handlers[hello.path]

                checkNotNull(init) { "" }

                val context = LiveViewContext(true, call.parameters)
                val view = init(context)

                send(Frame.Text(view.render()))

                // TODO: Send server-side events/react to server view state updates

                for (msg in incoming) {
                    val content = view.render()
                    send(Frame.Text(content))
                }
            }

            scope.installed = true
        }
    }

    fun view(path: String, init: LiveViewContext.() -> LiveView) {
        route.get(path) {
            val context = LiveViewContext(false, call.parameters)
            val view = init(context)

            val content = view.render()

            val type = ContentType.Text.Html.withCharset(Charsets.UTF_8)
            val ok = HttpStatusCode.OK

            call.respond(TextContent(content, type, ok))
        }

        handlers[path] = init
    }
}

fun Route.live(scope: LiveViewScope, block: LiveRouting.() -> Unit) = LiveRouting(this, scope).apply(block)

abstract class LiveView {
    abstract fun render(): String
}

fun LiveView.html(block: HTML.() -> Unit): String =
    buildString { append("<!DOCTYPE html>\n").appendHTML().html(block = block) }
