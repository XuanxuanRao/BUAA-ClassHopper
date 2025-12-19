package com.example.hello.service

import android.annotation.SuppressLint
import okhttp3.*
import okio.ByteString
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ChatWebSocketService(
    private val allowInsecureForDebug: Boolean = false
) {
    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        fun onMessage(bytes: ByteString)
        fun onClosing(code: Int, reason: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(error: String)

        fun onFailure(t: Throwable, response: Response?) {
            val responsePart = if (response != null) " (HTTP ${response.code} ${response.message})" else ""
            onFailure("${t.javaClass.simpleName}: ${t.message ?: "Unknown"}$responsePart")
        }
    }

    private val client: OkHttpClient = buildClient(allowInsecureForDebug)

    private var webSocket: WebSocket? = null
    private var currentToken: String? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var connecting: Boolean = false

    fun isConnected(): Boolean = connected
    fun isConnecting(): Boolean = connecting

    fun connect(token: String, listener: Listener) {
        // Close existing connection if any
        if (webSocket != null) {
            webSocket?.close(1000, "Reconnect")
            webSocket = null
        }

        currentToken = token
        val url = "wss://101.42.43.228/chat?token=$token"
        val request = Request.Builder()
            .url(url)
            .build()

        // update state
        connecting = true
        connected = false

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connected = true
                connecting = false
                listener.onOpen()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                listener.onMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                listener.onMessage(bytes)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                connecting = false
                listener.onClosing(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                listener.onClosed(code, reason)
                if (webSocket === ws) {
                    webSocket = null
                    currentToken = null
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                connected = false
                connecting = false
                listener.onFailure(t, response)
            }
        })
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun send(bytes: ByteString): Boolean {
        return webSocket?.send(bytes) ?: false
    }

    fun close(code: Int = 1000, reason: String = "Normal Closure") {
        webSocket?.close(code, reason)
        webSocket = null
        currentToken = null
        connected = false
        connecting = false
    }

    private fun buildClient(allowInsecure: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(30, TimeUnit.SECONDS)

        if (!allowInsecure) return builder.build()

        // WARNING: Only for debug/testing. Do NOT use in production.
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val trustManager = trustAllCerts[0] as X509TrustManager

        builder.sslSocketFactory(sslSocketFactory, trustManager)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
        return builder.build()
    }
}
