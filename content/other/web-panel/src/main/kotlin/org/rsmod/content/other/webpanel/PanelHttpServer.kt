package org.rsmod.content.other.webpanel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PanelHttpServer(
    private val port: Int,
    private val snapshot: () -> ServerView?,
    private val submit: (ActionRequest) -> CompletableFuture<ActionResult>,
) {
    private val mapper = jacksonObjectMapper()
    private val indexHtml: ByteArray by lazy {
        checkNotNull(javaClass.getResourceAsStream("/webpanel/index.html")) {
            "Missing /webpanel/index.html resource."
        }
            .use { it.readBytes() }
    }

    private var server: HttpServer? = null

    fun start() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        http.executor =
            Executors.newSingleThreadExecutor { Thread(it, "web-panel").apply { isDaemon = true } }
        http.createContext("/") { exchange -> exchange.handleSafely(::route) }
        http.start()
        server = http
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun route(exchange: HttpExchange) {
        if (!exchange.isLocalHost()) {
            exchange.send(403, "text/plain", "Forbidden".toByteArray())
            return
        }
        val path = exchange.requestURI.path
        when {
            exchange.requestMethod == "GET" && path == "/" ->
                exchange.send(200, "text/html; charset=utf-8", indexHtml)
            exchange.requestMethod == "GET" && path == "/api/status" -> {
                val view = snapshot()
                if (view == null) {
                    exchange.send(503, "text/plain", "Server is still starting.".toByteArray())
                } else {
                    exchange.sendJson(200, view)
                }
            }
            exchange.requestMethod == "POST" && path == "/api/action" -> handleAction(exchange)
            else -> exchange.send(404, "text/plain", "Not found".toByteArray())
        }
    }

    // Requiring a custom header forces a CORS preflight, which this server never approves, so
    // other websites open in the same browser cannot trigger admin actions.
    private fun handleAction(exchange: HttpExchange) {
        if (exchange.requestHeaders.getFirst(PANEL_HEADER) != "1") {
            exchange.send(403, "text/plain", "Forbidden".toByteArray())
            return
        }
        val request = mapper.readValue<ActionRequest>(exchange.requestBody)
        val result =
            try {
                submit(request).get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                ActionResult(ok = false, message = "Timed out waiting for the game tick.")
            }
        exchange.sendJson(200, result)
    }

    private fun HttpExchange.isLocalHost(): Boolean {
        val host = requestHeaders.getFirst("Host")?.substringBeforeLast(':') ?: return false
        return host == "127.0.0.1" || host == "localhost"
    }

    private fun HttpExchange.handleSafely(block: (HttpExchange) -> Unit) {
        try {
            block(this)
        } catch (e: Exception) {
            runCatching {
                send(500, "text/plain", (e.message ?: e.javaClass.simpleName).toByteArray())
            }
        } finally {
            close()
        }
    }

    private fun HttpExchange.sendJson(status: Int, body: Any) {
        send(status, "application/json", mapper.writeValueAsBytes(body))
    }

    private fun HttpExchange.send(status: Int, contentType: String, body: ByteArray) {
        responseHeaders.set("Content-Type", contentType)
        responseHeaders.set("Cache-Control", "no-store")
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private companion object {
        const val PANEL_HEADER = "X-Panel"
        const val ACTION_TIMEOUT_SECONDS = 3L
    }
}
