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
import kotlinx.coroutines.flow.*
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

@Serializable
sealed class LiveEvent

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

open class LiveViewState {
    private val changes = MutableSharedFlow<LiveViewState>(0, 1, BufferOverflow.DROP_LATEST)

    val updates = changes.asSharedFlow()

    fun <V> property(initial: V) = Delegates.observable(initial) { _, old, new ->
        if (new != old) {
            val self = this
            runBlocking { changes.emit(self) }
        }
    }
}

open class LiveOps() {
    private val reg: MutableMap<String, () -> Unit> = mutableMapOf()

    fun fn(identifier: String, body: () -> Unit): String {
        reg[identifier] = body
        return identifier
    }

    fun call(identifier: String) {
        val fn = checkNotNull(reg[identifier]) { "Function $identifier not registered" }
        fn()
    }
}

@Serializable
@SerialName("update")
data class LiveUpdate(val html: String) : LiveEvent()

@Serializable
@SerialName("refresh")
class LiveRefresh : LiveEvent()

@Serializable
@SerialName("invoke")
data class LiveInvocation(val identifier: String) : LiveEvent()

abstract class LiveView : CoroutineScope {
    private var xsession: LiveViewSession? = null
    private val session get() = checkNotNull(xsession) { "View is not connected" }

    override val coroutineContext get() = session.coroutineContext

    val connected: Boolean get() = xsession != null

    protected open val ops = LiveOps()

    protected abstract val state: LiveViewState

    abstract fun mount()

    abstract fun render(): String

    private fun dispatch(identifier: String) = ops.call(identifier)

    private suspend fun send(event: LiveEvent) = session.send(event)

    private suspend fun receive() = session.receive()

    private suspend fun handle(event: LiveEvent) {
        when (event) {
            is LiveRefresh -> send(LiveUpdate(render()))
            is LiveInvocation -> dispatch(event.identifier)
            is LiveUpdate -> TODO("This is an event only intended to be sent to the client, not received here")
        }
    }

    internal suspend fun join(session: LiveViewSession) {
        check(this.xsession == null) { "View is joined to another session already" }
        this.xsession = session

        val updates = state.updates.map { LiveRefresh() }

        val incoming = flow {
            while (session.isActive) {
                val event = receive()
                emit(event)
            }
        }

        val events = merge(updates, incoming)

        try {
            mount()

            events.fold(0) { count, event ->
                handle(event)
                count + 1
            }
        } catch (e: ClosedReceiveChannelException) {
            // …
        } finally {
            this.xsession = null
        }
    }
}

@Suppress("UnusedReceiverParameter")
fun LiveView.html(block: HTML.() -> Unit): String =
    buildString { append("<!DOCTYPE html>\n").appendHTML().html(block =  block) }

class HTMLTagLiveAttributes(private val tag: HTMLTag) {
    operator fun set(key: String, value: String) { tag.attributes[name(key)] = value }

    operator fun get(key: String): String? = tag.attributes[name(key)]

    companion object {
        const val prefix = "data-ktor"
        fun name(key: String) = "$prefix-$key"
    }
}

val HTMLTag.live get() = HTMLTagLiveAttributes(this)
