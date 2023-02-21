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
    internal val factories = mutableMapOf<String, LiveViewFactoryInterface<LiveViewState, LiveView<LiveViewState>>>()
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

@Serializable
sealed class LiveUpdate

class LiveViewSession(private val session: DefaultWebSocketServerSession) : CoroutineScope by session {
    suspend fun send(update: LiveUpdate) = session.sendSerialized(update)

    suspend fun flush() = session.flush()

    suspend fun receive() = session.receiveDeserialized<LiveEvent>()
}

@Serializable
data class LiveConnect(val path: String, val state: LiveViewState)

class LiveRouting(private val routing: Routing, private val scope: LiveViewScope) {
    init {
        routing.application.plugin(WebSockets) // require plugin to be installed

        if (!scope.installed) {
            routing.webSocket(scope.endpoint) {
                val connect = receiveDeserialized<LiveConnect>()
                val factory = scope.factories[connect.path]

                checkNotNull(factory) { "No view registered for path ${connect.path}" }

                val session = LiveViewSession(this)
                val view = factory.create(connect.state)
                view.join(session)
            }

            scope.installed = true
        }
    }

    fun <S : LiveViewState, V : LiveView<S>> view(path: String, factory: LiveViewFactoryInterface<S, V>, init: LiveViewContext.() -> S) {
        routing.get(path) {
            val context = LiveViewContext(call.application, call.parameters)
            val view = factory.create(init(context))
            view.mount()

            val content = view.render()

            val type = ContentType.Text.Html.withCharset(Charsets.UTF_8)
            val ok = HttpStatusCode.OK

            call.respond(TextContent(content, type, ok))
        }

        scope.factories[path] = factory as LiveViewFactoryInterface<LiveViewState, LiveView<LiveViewState>>
    }
}

fun Routing.live(scope: LiveViewScope, block: LiveRouting.() -> Unit) = LiveRouting(this, scope).apply(block)

@Serializable
abstract class LiveViewState {
    @Transient
    private val changes = MutableSharedFlow<LiveViewState>(0, 1, BufferOverflow.DROP_LATEST)

    @Transient
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

    internal fun call(identifier: String) {
        val fn = reg[identifier]

        if (fn == null) {
            handle(identifier)
        } else {
            fn()
        }
    }

    open fun handle(identifier: String) {
        throw NotImplementedError("Unhandled invocation: \"$identifier\"")
    }
}

@Serializable
@SerialName("render")
data class LiveRender(val html: String) : LiveUpdate()

@Serializable
@SerialName("refresh")
object LiveRefresh : LiveEvent()

@Serializable
@SerialName("invoke")
data class LiveInvocation(val identifier: String) : LiveEvent()

fun interface LiveViewFactoryInterface<State : LiveViewState, View : LiveView<State>> {
    fun create(state: State): View
}

open class LiveViewFactory<State : LiveViewState, View : LiveView<State>>(private val block: (State) -> View) : LiveViewFactoryInterface<State, View> {
    override fun create(state: State): View = block(state)
}

abstract class LiveView<State : LiveViewState>(protected val state: State) : CoroutineScope {
    private var xsession: LiveViewSession? = null
    private val session get() = checkNotNull(xsession) { "View is not connected" }

    override val coroutineContext get() = session.coroutineContext

    val connected: Boolean get() = xsession != null

    open val ops = LiveOps()

    abstract fun mount()

    abstract fun render(): String

    private fun dispatch(identifier: String) = ops.call(identifier)

    private suspend fun send(update: LiveUpdate) = session.send(update)

    private suspend fun receive() = session.receive()

    private suspend fun handle(event: LiveEvent) {
        when (event) {
            is LiveRefresh -> send(LiveRender(render()))
            is LiveInvocation -> dispatch(event.identifier)
        }
    }

    internal suspend fun join(session: LiveViewSession) {
        check(this.xsession == null) { "View is joined to another session already" }
        this.xsession = session

        val updates = state.updates.map { LiveRefresh }

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
fun LiveView<*>.html(block: HTML.() -> Unit): String =
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
