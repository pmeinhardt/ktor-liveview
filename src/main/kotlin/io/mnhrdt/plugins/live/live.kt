package io.mnhrdt.plugins.live

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlinx.serialization.*

class LiveViewScope private constructor(options: Options) {
    internal val handlers = mutableMapOf<String, LiveViewContext.() -> LiveView>()
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

class LiveViewContext(val application: Application, val parameters: Parameters)

interface LiveEvent // TODO: Implementing classes need to be serializable (fun serializer(): KSerializer<…>)

@Serializable
data class LiveUpdate(val html: String) : LiveEvent

class LiveViewSession(private val session: DefaultWebSocketServerSession) : CoroutineScope by session {
    suspend fun send(event: LiveEvent) = session.sendSerialized(event)

    suspend fun flush() = session.flush()

    suspend fun receive() = session.receiveDeserialized<LiveEvent>()
}

@Serializable
data class LiveConnect(val path: String, val parameters: Map<String, List<String>>)

class LiveRouting(private val routing: Routing, private val scope: LiveViewScope) {
    init {
        routing.application.plugin(WebSockets) // require plugin to be installed

        if (!scope.installed) {
            routing.webSocket(scope.endpoint) {
                val connect = receiveDeserialized<LiveConnect>()
                val init = scope.handlers[connect.path]

                checkNotNull(init) { "No view registered for path ${connect.path}" }

                val context = LiveViewContext(application, parametersOf(connect.parameters))
                val session = LiveViewSession(this)

                val view = init(context)
                view.join(session)
            }

            scope.installed = true
        }
    }

    fun view(path: String, params: Parameters.() -> Parameters = { this }, init: LiveViewContext.() -> LiveView) {
        routing.get(path) {
            val context = LiveViewContext(call.application, params(call.parameters))
            val view = init(context)
            view.mount()

            val content = view.render()

            val type = ContentType.Text.Html.withCharset(Charsets.UTF_8)
            val ok = HttpStatusCode.OK

            call.respond(TextContent(content, type, ok))
        }

        scope.handlers[path] = init
    }
}

fun Routing.live(scope: LiveViewScope, block: LiveRouting.() -> Unit) = LiveRouting(this, scope).apply(block)

abstract class LiveView : CoroutineScope {
    override val coroutineContext get() = checkNotNull(session) { "View is not connected" }.coroutineContext

    private var session: LiveViewSession? = null
    val connected: Boolean get() = session != null

    private val state = mutableMapOf<String, Any>()
    val assigns = state as Map<String, Any>

    private var pending: Deferred<*>? = null

    abstract fun mount()

    abstract fun render(): String

    fun assign(key: String, value: Any) {
        pending?.cancel()
        state[key] = value
        pending = session?.async { send(LiveUpdate(render())) }
    }

    private suspend fun handle(event: LiveEvent) {}

    private suspend fun send(event: LiveEvent) {
        val session = checkNotNull(session) { "View is not connected" }
        session.send(event)
    }

    internal suspend fun join(session: LiveViewSession) {
        check(this.session == null) { "View is joined to another session already" }
        this.session = session

        try {
            mount()

            while (session.isActive) {
                val event = session.receive()
                println("Received LiveEvent: $event")
                // TODO: handle events
            }
        } catch (e: ClosedReceiveChannelException) {
            // …
        } finally {
            this.session = null
        }
    }
}

fun LiveView.html(block: HTML.() -> Unit): String =
    buildString { append("<!DOCTYPE html>\n").appendHTML().html(block = block) }
