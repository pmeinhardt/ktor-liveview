@file:Suppress("unused")

package io.mnhrdt.plugins.live

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.properties.*
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

    fun view(path: String, init: LiveViewContext.() -> LiveView) {
        routing.get(path) {
            val context = LiveViewContext(call.application, call.parameters)
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

typealias Subscription<V> = (V) -> Unit
typealias Unsubscribe = () -> Unit

open class Observable<V> {
    private val subscriptions = mutableSetOf<Subscription<V>>()

    protected fun update(value: V) {
        subscriptions.forEach { it(value) }
    }

    fun subscribe(callback: Subscription<V>): Unsubscribe {
        val unsubscribe = { subscriptions -= callback }
        subscriptions += callback
        return unsubscribe
    }
}

open class LiveViewState : Observable<LiveViewState>() {
    fun <V> property(initial: V) = Delegates.observable(initial) { _, _, _ -> update(this) }
}

@Serializable
data class LiveUpdate(val html: String) : LiveEvent

abstract class LiveView : CoroutineScope {
    private var xsession: LiveViewSession? = null
    private val session get() = checkNotNull(xsession) { "View is not connected" }

    override val coroutineContext get() = session.coroutineContext

    val connected: Boolean get() = xsession != null

    abstract val state: LiveViewState

    abstract fun mount()

    abstract fun render(): String

    private suspend fun send(event: LiveEvent) = session.send(event)

    private suspend fun receive() = session.receive()

    internal suspend fun join(session: LiveViewSession) {
        check(this.xsession == null) { "View is joined to another session already" }
        this.xsession = session

        var pending: Deferred<*>? = null

        val unsubscribe = state.subscribe {
            pending?.cancel()
            pending = async { send(LiveUpdate(render())) }
        }

        try {
            mount()

            while (isActive) {
                val event = receive()
                println("Received LiveEvent: $event")
                // TODO: handle events
            }
        } catch (e: ClosedReceiveChannelException) {
            // …
        } finally {
            unsubscribe()
            pending?.cancel()
            this.xsession = null
        }
    }
}

@Suppress("UnusedReceiverParameter")
fun LiveView.html(block: HTML.() -> Unit): String =
    buildString { append("<!DOCTYPE html>\n").appendHTML().html(block = block) }
