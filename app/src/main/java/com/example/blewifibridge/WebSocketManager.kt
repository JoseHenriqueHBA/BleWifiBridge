package com.example.blewifibridge

import okhttp3.*
import java.util.concurrent.TimeUnit

interface WsListener {
    fun onLog(message: String)
    fun onOpen()
    fun onClosed()
}

class WebSocketManager(private val listener: WsListener) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // conexão de longa duração
        .pingInterval(20, TimeUnit.SECONDS)    // mantém viva / detecta queda
        .build()

    private var webSocket: WebSocket? = null

    /** ip:porta do computador, ex: "192.168.0.10:8765" */
    fun connect(ip: String, port: String) {
        val url = "ws://$ip:$port"
        listener.onLog("Conectando ao servidor $url ...")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onLog("Conectado ao servidor.")
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onLog("Servidor disse: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onLog("Servidor fechando conexão: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onLog("Erro na conexão com servidor: ${t.message}")
                listener.onClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }
        })
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Encerrado pelo app")
        webSocket = null
    }
}
