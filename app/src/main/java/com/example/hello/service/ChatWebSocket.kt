package com.example.hello.service

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.*
import okio.ByteString
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
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

        /** Called before an automatic reconnect attempt is scheduled. */
        fun onReconnectAttempt(attempt: Int, delayMs: Long) {}

        fun onFailure(t: Throwable, response: Response?) {
            val responsePart = if (response != null) " (HTTP ${response.code} ${response.message})" else ""
            onFailure("${t.javaClass.simpleName}: ${t.message ?: "Unknown"}$responsePart")
        }
    }

    private val client: OkHttpClient = buildClient(allowInsecureForDebug)

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var reconnectFuture: ScheduledFuture<*>? = null

    private var webSocket: WebSocket? = null
    private var currentToken: String? = null
    private var currentListener: Listener? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var connecting: Boolean = false
    @Volatile private var manualClose: Boolean = false
    @Volatile private var autoReconnectEnabled: Boolean = true
    @Volatile private var reconnectAttempt: Int = 0

    fun isConnected(): Boolean = connected
    fun isConnecting(): Boolean = connecting

    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) {
            reconnectFuture?.cancel(false)
            reconnectFuture = null
        }
    }

    fun connect(token: String, listener: Listener) {
        connectInternal(token, listener, resetBackoff = true)
    }

    private fun connectInternal(token: String, listener: Listener, resetBackoff: Boolean) {
        Log.i("WebSocket", "connectInternal")
        manualClose = false
        currentListener = listener

        if (resetBackoff) {
            // Cancel any pending reconnect
            reconnectFuture?.cancel(false)
            reconnectFuture = null
            reconnectAttempt = 0
        }

        // Close existing connection if any
        if (webSocket != null) {
            // explicit close: do not schedule reconnect from this close
            manualClose = true
            webSocket?.close(1000, "Reconnect")
            webSocket = null
            manualClose = false
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
                reconnectAttempt = 0
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

                maybeScheduleReconnect("closed")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                connected = false
                connecting = false
                listener.onFailure(t, response)

                maybeScheduleReconnect("failure")
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
        manualClose = true
        reconnectFuture?.cancel(false)
        reconnectFuture = null
        webSocket?.close(code, reason)
        webSocket = null
        currentToken = null
        currentListener = null
        connected = false
        connecting = false
        reconnectAttempt = 0
        manualClose = false
    }

    private fun maybeScheduleReconnect(origin: String) {
        if (manualClose) return
        if (!autoReconnectEnabled) return
        val token = currentToken ?: return
        val listener = currentListener ?: return
        if (connecting || connected) return

        reconnectAttempt += 1

        // Exponential backoff: 1s, 2s, 4s... max 30s
        val delayMs = (1000L shl (reconnectAttempt - 1)).coerceAtMost(30_000L)
        listener.onReconnectAttempt(reconnectAttempt, delayMs)

        reconnectFuture?.cancel(false)
        reconnectFuture = scheduler.schedule(
            {
                // Guard again at execution time
                if (manualClose || connected || connecting) return@schedule
                connectInternal(token, listener, resetBackoff = false)
            },
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun buildClient(allowInsecure: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(10, TimeUnit.SECONDS) // 缩短心跳间隔，更快检测断连
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)

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
