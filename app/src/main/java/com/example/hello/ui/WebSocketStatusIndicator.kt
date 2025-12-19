package com.example.hello.ui

import android.content.Context
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.hello.R

class WebSocketStatusIndicator(
    private val context: Context,
    private val iconView: ImageView,
) {
    fun showConnected() {
        iconView.setImageResource(android.R.drawable.presence_online)
        iconView.setColorFilter(ContextCompat.getColor(context, R.color.ws_connected))
        iconView.contentDescription = "WebSocket 已连接"
    }

    fun showConnecting() {
        iconView.setImageResource(android.R.drawable.presence_away)
        iconView.setColorFilter(ContextCompat.getColor(context, R.color.ws_connecting))
        iconView.contentDescription = "WebSocket 连接中"
    }

    fun showDisconnected() {
        iconView.setImageResource(android.R.drawable.presence_offline)
        iconView.setColorFilter(ContextCompat.getColor(context, R.color.ws_disconnected))
        iconView.contentDescription = "WebSocket 未连接"
    }
}

