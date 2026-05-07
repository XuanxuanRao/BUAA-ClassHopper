package top.aidanrao.buaa_classhopper.ui

import android.content.Context
import android.widget.ImageView
import androidx.core.content.ContextCompat
import top.aidanrao.buaa_classhopper.R

class IClassAvailabilityIndicator(
    private val context: Context,
    private val iconView: ImageView,
) {
    fun showReachable() {
        iconView.setImageResource(android.R.drawable.presence_online)
        iconView.setColorFilter(ContextCompat.getColor(context, R.color.ws_connected))
        iconView.contentDescription = "iClass 可访问"
    }

    fun showUnreachable() {
        iconView.setImageResource(android.R.drawable.presence_busy)
        iconView.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_red_dark))
        iconView.contentDescription = "iClass 无法访问"
    }
}
