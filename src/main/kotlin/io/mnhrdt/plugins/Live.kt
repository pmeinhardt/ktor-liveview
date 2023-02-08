package io.mnhrdt.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.html.*
import kotlinx.html.stream.*

class LiveViewContext(val connected: Boolean, val parameters: Parameters)

abstract class LiveView {
    abstract fun render(): String
}

fun LiveView.html(block: HTML.() -> Unit): String =
    buildString { append("<!DOCTYPE html>\n").appendHTML().html(block = block) }

fun Route.live(path: String, init: LiveViewContext.() -> LiveView) {
    get(path) {
        val context = LiveViewContext(false, call.parameters)
        val view = init(context)

        val content = view.render()

        val type = ContentType.Text.Html.withCharset(Charsets.UTF_8)
        val ok = HttpStatusCode.OK

        call.respond(TextContent(content, type, ok))
    }

    webSocket(path) {
        val context = LiveViewContext(true, call.parameters)
        val view = init(context)

        for (msg in incoming) {
            val content = view.render()
            send(Frame.Text(content))
        }
    }
}
