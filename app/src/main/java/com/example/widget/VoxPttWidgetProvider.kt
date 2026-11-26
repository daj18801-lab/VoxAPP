package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.media.LivePttSessionManager

/**
 * Persistent Home Screen Push-to-Talk Widget for VOX.
 *
 * Allows users to run VOX in the background and tap the widget icon directly on their home screen:
 * - First tap: activates Push-to-Talk transmission and lights up the widget with a glowing ON AIR halo.
 * - Second tap: stops transmitting and returns the widget to its sleek dark idle state.
 */
class VoxPttWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_PTT -> {
                val handled = LivePttSessionManager.toggleFromWidget(context)
                if (!handled) {
                    // No saved PTT session yet or missing mic permission — open VOX to connect PTT
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    runCatching { context.startActivity(launchIntent) }
                }
                requestUpdate(context)
            }
            ACTION_REFRESH_WIDGET -> {
                requestUpdate(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PTT = "com.example.widget.ACTION_TOGGLE_PTT"
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"

        fun requestUpdate(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, VoxPttWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, manager, ids)
                }
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val isTransmitting = LivePttSessionManager.isTransmitting.value
            val isConnected = LivePttSessionManager.isConnected.value
            val isConnecting = LivePttSessionManager.isConnecting.value
            val peerName = LivePttSessionManager.activePeerName.value
                .ifBlank { LivePttSessionManager.getSavedLastPeerName(context) }

            val toggleIntent = Intent(context, VoxPttWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_PTT
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                9901,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_vox_ptt)
                views.setInt(
                    R.id.ptt_widget_button,
                    "setBackgroundResource",
                    if (isTransmitting) R.drawable.bg_ptt_widget_lit else R.drawable.bg_ptt_widget_idle
                )
                views.setTextViewText(
                    R.id.ptt_widget_status,
                    when {
                        isTransmitting -> "ON AIR"
                        isConnecting -> "SYNC…"
                        else -> "VOX PTT"
                    }
                )
                views.setTextViewText(
                    R.id.ptt_widget_peer,
                    when {
                        isTransmitting && peerName.isNotBlank() -> "LIVE · ${peerName.take(12)}"
                        isTransmitting -> "TRANSMITTING"
                        isConnected && peerName.isNotBlank() -> peerName.take(14)
                        peerName.isNotBlank() -> "Tap · ${peerName.take(10)}"
                        else -> "Tap to Talk"
                    }
                )
                views.setOnClickPendingIntent(R.id.ptt_widget_button, togglePendingIntent)
                views.setOnClickPendingIntent(R.id.ptt_widget_root, togglePendingIntent)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}

object VoxPttWidgetHelper {
    /**
     * Prompts the Android launcher to pin the VOX PTT widget directly to the user's home screen.
     * Returns a user-friendly status message explaining the result.
     */
    fun requestPinPttWidget(context: Context): String {
        return runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, VoxPttWidgetProvider::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
                val requested = manager.requestPinAppWidget(provider, null, null)
                if (requested) {
                    "Confirm on your home screen to add the VOX PTT widget!"
                } else {
                    "Long-press your home screen -> Widgets -> VOX PTT to add it."
                }
            } else {
                "Long-press your home screen -> Widgets -> VOX PTT to place the widget."
            }
        }.getOrElse {
            "Long-press your home screen -> Widgets -> VOX PTT to place the widget."
        }
    }
}
