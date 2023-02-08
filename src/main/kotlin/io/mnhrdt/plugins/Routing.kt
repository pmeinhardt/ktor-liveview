package io.mnhrdt.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.routing.*
import java.util.Date
import kotlinx.html.*

class Index(private val name: String) : LiveView() {
    override fun render(): String =
        html {
            head { title { +"Hello $name" } }
            body { p { +"It is ${now()}" } }
        }

    private fun now() = Date()
}

fun Application.configureRouting() {
    install(AutoHeadResponse)

    routing {
        live("/") {
            val name = parameters["name"] ?: "Ktor"
            Index(name)
        }
    }
}
