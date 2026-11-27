package com.example.media

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Microphone Foreground Service that:
 * 1. Keeps active Private Message voice calls running uninterrupted when the app is minimized.
 * 2. Keeps Mutual Live Push-to-Talk walkie-talkie audio streaming alive in the background AND
 *    displays a draggable floating Push-to-Talk button overlay when the app is minimized
 *    (if `Settings.canDrawOverlays` is granted, with a notification Talk/Mute fallback otherwise).
 */
class PrivateCallForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var floatingRootView: View? = null
    private var floatingLayoutParams: WindowManager.LayoutParams? = null
    private var floatingButtonCircle: TextView? = null
    private var floatingPeerPill: TextView? = null
    private var closeConfirmOverlayView: View? = null
    private var overlayStateJob: Job? = null
    private var currentThreadId: String = ""
    private var currentPeerName: String = "Private Chat"
    private var overlayDismissedByUserForBackgroundCycle: Boolean = false
    private var screenOffReceiverRegistered: Boolean = false
    private var lastLockScreenLaunchMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != Intent.ACTION_SCREEN_OFF && action != Intent.ACTION_SCREEN_ON) return
            if (!isPttMode) return
            if (LivePttSessionManager.activeThreadId.value.isNullOrBlank()) return
            if (!DirectMessageNotificationPrefs.isLockScreenPttEnabled(this@PrivateCallForegroundService)) return
            if (action == Intent.ACTION_SCREEN_ON) {
                val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                if (km != null && !km.isKeyguardLocked) return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastLockScreenLaunchMs < 1_500L) return
            lastLockScreenLaunchMs = now
            launchLockScreenPttActivity()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val threadId = intent?.getStringExtra(EXTRA_THREAD_ID).orEmpty().ifBlank { currentThreadId }
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { currentPeerName }

        when (action) {
            ACTION_END_CALL -> {
                if (isPttMode) {
                    LivePttSessionManager.disconnect(this)
                } else if (threadId.isNotBlank()) {
                    _endCallEvents.tryEmit(threadId)
                }
                removeCloseConfirmOverlay()
                removeFloatingOverlay()
                unregisterScreenOffReceiver()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_PTT -> {
                LivePttSessionManager.toggleTransmitting(this)
                return START_NOT_STICKY
            }

            ACTION_HIDE_FLOATING_PTT -> {
                overlayDismissedByUserForBackgroundCycle = false
                removeCloseConfirmOverlay()
                removeFloatingOverlay()
                return START_NOT_STICKY
            }
        }

        val pttMode = intent?.getBooleanExtra(EXTRA_IS_PTT_MODE, false) == true
        val pttTransmitting = intent?.getBooleanExtra(EXTRA_PTT_TRANSMITTING, false) == true
        val showFloatingOverlay = intent?.getBooleanExtra(EXTRA_SHOW_FLOATING_OVERLAY, false) == true

        if (threadId.isNotBlank() && threadId != currentThreadId) {
            overlayDismissedByUserForBackgroundCycle = false
            currentThreadId = threadId
        }
        if (peerName.isNotBlank()) currentPeerName = peerName

        ensureCallChannel(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (currentThreadId.isNotBlank()) {
                putExtra(DirectMessageNotificationHelper.EXTRA_DM_THREAD_ID, currentThreadId)
            }
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, PrivateCallForegroundService::class.java).apply {
            this.action = ACTION_END_CALL
            putExtra(EXTRA_THREAD_ID, currentThreadId)
        }
        val endPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockScreenEnabled = DirectMessageNotificationPrefs.isLockScreenPttEnabled(this)
        val effectiveChannelId = if (pttMode && lockScreenEnabled) LOCK_SCREEN_CHANNEL_ID else CHANNEL_ID

        val notificationBuilder = NotificationCompat.Builder(this, effectiveChannelId)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(if (pttTransmitting) 0xFFFF4D6D.toInt() else 0xFF0088FF.toInt())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (pttMode && lockScreenEnabled) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(openPendingIntent)

        if (pttMode) {
            val toggleIntent = Intent(this, PrivateCallForegroundService::class.java).apply {
                this.action = ACTION_TOGGLE_PTT
                putExtra(EXTRA_THREAD_ID, currentThreadId)
                putExtra(EXTRA_PEER_NAME, currentPeerName)
            }
            val togglePendingIntent = PendingIntent.getService(
                this,
                NOTIFICATION_ID + 2,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val lockScreenIntent = Intent(this, LockScreenPttActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val lockScreenPendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID + 3,
                lockScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val isVttStandby = !LivePttSessionManager.activeThreadHasLiveListener.value ||
                LivePttSessionManager.activeThreadSenderSelfVttOn.value ||
                LivePttSessionManager.isPttVoiceToTextEnabled.value ||
                LivePttSessionManager.isPttSilentMode.value
            if (lockScreenEnabled) {
                notificationBuilder.setContentIntent(lockScreenPendingIntent)
            }
            notificationBuilder
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(
                    when {
                        pttTransmitting && isVttStandby -> "PTT Voice-to-Text Listening · $currentPeerName"
                        pttTransmitting -> "LIVE PTT Transmitting · $currentPeerName"
                        isVttStandby -> "Lock-Screen PTT (Voice-to-Text) · $currentPeerName"
                        else -> "Live Push-to-Talk Ready · $currentPeerName"
                    }
                )
                .setContentText(
                    when {
                        pttTransmitting && isVttStandby -> "Transcribing Voice-to-Text — no live audio sent"
                        pttTransmitting -> "Streaming live audio — release button or tap Mute"
                        isVttStandby && lockScreenEnabled -> "Peer PTT off or VTT mode — tap to open Lock-Screen PTT"
                        lockScreenEnabled -> "Tap to open Lock-Screen PTT or hold floating button"
                        else -> "Hold floating button or tap Talk Live to stream live audio"
                    }
                )
                .addAction(
                    0,
                    if (pttTransmitting) "Stop PTT" else if (isVttStandby) "Voice-to-Text" else "Talk Live",
                    togglePendingIntent
                )
            if (lockScreenEnabled) {
                notificationBuilder.addAction(
                    0,
                    "Lock-Screen PTT",
                    lockScreenPendingIntent
                )
            }
            notificationBuilder.addAction(
                0,
                "End PTT",
                endPendingIntent
            )
        } else {
            notificationBuilder
                .setContentTitle("Private Voice Call · $currentPeerName")
                .setContentText("Microphone active in background — tap to return")
                .addAction(
                    0,
                    getString(R.string.action_end_call),
                    endPendingIntent
                )
        }

        try {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notificationBuilder.build(), serviceType)
            isRunning = true
            isPttMode = pttMode
            activeThreadId = currentThreadId
        } catch (err: Exception) {
            Log.w(TAG, "Unable to enter microphone foreground service", err)
            stopSelf()
            return START_NOT_STICKY
        }

        if (pttMode) {
            registerScreenOffReceiver()
        } else {
            unregisterScreenOffReceiver()
        }

        if (pttMode && showFloatingOverlay) {
            if (!overlayDismissedByUserForBackgroundCycle) {
                showOrUpdateFloatingOverlay()
            }
        } else if (!showFloatingOverlay) {
            overlayDismissedByUserForBackgroundCycle = false
            removeFloatingOverlay()
        }

        return START_NOT_STICKY
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenOffReceiverRegistered = true
        } catch (err: Exception) {
            Log.w(TAG, "Unable to register screen-off receiver for lock-screen PTT", err)
        }
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffReceiverRegistered) return
        screenOffReceiverRegistered = false
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    private fun launchLockScreenPttActivity() {
        try {
            val intent = Intent(this, LockScreenPttActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
                val pi = PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID + 4,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                runCatching { pi.send(this, 0, null, null, null, null, options) }
                    .onFailure { startActivity(intent) }
            } else {
                startActivity(intent)
            }
        } catch (err: Exception) {
            Log.w(TAG, "Unable to launch LockScreenPttActivity on screen lock", err)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdateFloatingOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        val wm = windowManager ?: (getSystemService(WINDOW_SERVICE) as? WindowManager).also {
            windowManager = it
        } ?: return

        if (floatingRootView != null) {
            floatingPeerPill?.text = currentPeerName.take(14)
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val circleSize = dp(68)
        val circleView = TextView(this).apply {
            text = "PTT"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            elevation = dp(8).toFloat()
            contentDescription = "Hold to stream live push to talk audio to $currentPeerName"
            background = createCircleBackground(COLOR_STANDBY_EMERALD, COLOR_WHITE_BORDER)
        }
        val circleParams = LinearLayout.LayoutParams(circleSize, circleSize)
        root.addView(circleView, circleParams)

        // Compact companion bar: peer name (tap to open chat) + tiny close X
        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(0xDD111827.toInt())
                setStroke(dp(1), 0x44FFFFFF)
            }
        }

        val peerLabel = TextView(this).apply {
            text = currentPeerName.take(14)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
            setOnClickListener {
                val openIntent = Intent(this@PrivateCallForegroundService, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (currentThreadId.isNotBlank()) {
                        putExtra(DirectMessageNotificationHelper.EXTRA_DM_THREAD_ID, currentThreadId)
                    }
                }
                startActivity(openIntent)
            }
        }
        val closeLabel = TextView(this).apply {
            text = "✕"
            setTextColor(0xFFD1D5DB.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, dp(2), 0)
            contentDescription = "Close floating push to talk button"
            setOnClickListener {
                LivePttSessionManager.setTransmitting(this@PrivateCallForegroundService, false)
                if (!LivePttSessionManager.isAppInBackground.value) {
                    LivePttSessionManager.requestFloatingPttClosePrompt(currentThreadId, currentPeerName)
                } else {
                    showCloseConfirmOverlay()
                }
            }
        }
        pillRow.addView(peerLabel)
        pillRow.addView(closeLabel)

        val pillParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
        }
        root.addView(pillRow, pillParams)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(180)
        }

        val dragThresholdPx = dp(16)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var startedTxOnDown = false

        circleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    startedTxOnDown = true
                    triggerShortHaptic()
                    LivePttSessionManager.setTransmitting(this@PrivateCallForegroundService, true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx)) {
                        isDragging = true
                        if (startedTxOnDown) {
                            startedTxOnDown = false
                            LivePttSessionManager.setTransmitting(this@PrivateCallForegroundService, false)
                        }
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = (initialY + dy).coerceAtLeast(dp(32))
                        runCatching { wm.updateViewLayout(root, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (startedTxOnDown) {
                        startedTxOnDown = false
                        LivePttSessionManager.setTransmitting(this@PrivateCallForegroundService, false)
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }

        try {
            wm.addView(root, params)
            floatingRootView = root
            floatingLayoutParams = params
            floatingButtonCircle = circleView
            floatingPeerPill = peerLabel
            startObservingOverlayVisualState()
        } catch (err: Exception) {
            Log.w(TAG, "Unable to attach floating PTT button overlay", err)
        }
    }

    private fun startObservingOverlayVisualState() {
        overlayStateJob?.cancel()
        overlayStateJob = serviceScope.launch {
            combine(
                LivePttSessionManager.isTransmitting,
                LivePttSessionManager.remoteSpeakingName,
                LivePttSessionManager.isConnected,
                LivePttSessionManager.activePttTheme
            ) { transmitting, remoteSpeaker, connected, pttTheme ->
                OverlayVisualState(transmitting, remoteSpeaker, connected, pttTheme)
            }.collect { (transmitting, remoteSpeaker, connected, pttTheme) ->
                val circle = floatingButtonCircle ?: return@collect
                val standbyColor = runCatching { Color.parseColor(pttTheme.standbyColorHex) }
                    .getOrDefault(COLOR_STANDBY_EMERALD)
                val liveColor = runCatching { Color.parseColor(pttTheme.liveColorHex) }
                    .getOrDefault(COLOR_LIVE_CORAL)
                val glowStroke = if (pttTheme.hasPulseRing) {
                    runCatching { Color.parseColor(pttTheme.ringGlowHex) }.getOrDefault(COLOR_WHITE_BORDER)
                } else {
                    COLOR_WHITE_BORDER
                }
                when {
                    transmitting -> {
                        circle.text = "LIVE"
                        circle.background = createCircleBackground(liveColor, glowStroke)
                    }
                    !remoteSpeaker.isNullOrBlank() -> {
                        circle.text = "HEAR"
                        circle.background = createCircleBackground(COLOR_INCOMING_BLUE, glowStroke)
                    }
                    connected -> {
                        circle.text = "PTT"
                        circle.background = createCircleBackground(standbyColor, glowStroke)
                    }
                    else -> {
                        circle.text = "..."
                        circle.background = createCircleBackground(COLOR_CONNECTING_SLATE, glowStroke)
                    }
                }
            }
        }
    }

    private data class OverlayVisualState(
        val transmitting: Boolean,
        val remoteSpeaker: String?,
        val connected: Boolean,
        val pttTheme: com.example.data.PttThemeStyle
    )

    private fun createCircleBackground(fillColor: Int, strokeColor: Int): GradientDrawable {
        val strokePx = (2.5f * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(strokePx, strokeColor)
        }
    }

    private fun triggerShortHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(32L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(32L)
            }
        } catch (_: Exception) {
            // Ignored
        }
    }

    private fun showCloseConfirmOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            LivePttSessionManager.requestFloatingPttClosePrompt(currentThreadId, currentPeerName)
            return
        }
        val wm = windowManager ?: (getSystemService(WINDOW_SERVICE) as? WindowManager).also {
            windowManager = it
        } ?: return

        removeCloseConfirmOverlay()

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xF5111827.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            elevation = dp(14).toFloat()
        }

        val titleView = TextView(this).apply {
            text = "Close PTT Stream?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val messageView = TextView(this).apply {
            text = "Do you want to end the Push-to-Talk stream with $currentPeerName, or keep it open with a circle PTT button inside the Private DM?"
            setTextColor(0xFFD1D5DB.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(8), 0, dp(14))
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val keepBtn = TextView(this).apply {
            text = "No, Keep Open"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(COLOR_INCOMING_BLUE)
            }
            setOnClickListener {
                removeCloseConfirmOverlay()
                LivePttSessionManager.dockFloatingPttToDm(this@PrivateCallForegroundService, currentThreadId)
            }
        }

        val endBtn = TextView(this).apply {
            text = "Yes, End PTT"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(COLOR_LIVE_CORAL)
            }
            setOnClickListener {
                removeCloseConfirmOverlay()
                LivePttSessionManager.endPttSessionAndDisableInThread(this@PrivateCallForegroundService, currentThreadId)
            }
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextColor(0xFF9CA3AF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(4))
            setOnClickListener {
                removeCloseConfirmOverlay()
            }
        }

        val keepParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) }
        val endParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonsRow.addView(keepBtn, keepParams)
        buttonsRow.addView(endBtn, endParams)

        card.addView(titleView)
        card.addView(messageView)
        card.addView(buttonsRow)
        card.addView(
            cancelBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        )

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cardWidthPx = (resources.displayMetrics.widthPixels - dp(40)).coerceAtMost(dp(330))
        val params = WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.45f
        }

        try {
            wm.addView(card, params)
            closeConfirmOverlayView = card
        } catch (err: Exception) {
            Log.w(TAG, "Unable to show floating PTT close confirm overlay", err)
            LivePttSessionManager.requestFloatingPttClosePrompt(currentThreadId, currentPeerName)
        }
    }

    private fun removeCloseConfirmOverlay() {
        val view = closeConfirmOverlayView
        closeConfirmOverlayView = null
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
        }
    }

    private fun removeFloatingOverlay() {
        overlayStateJob?.cancel()
        overlayStateJob = null
        val root = floatingRootView
        floatingRootView = null
        floatingButtonCircle = null
        floatingPeerPill = null
        floatingLayoutParams = null
        if (root != null) {
            runCatching { windowManager?.removeView(root) }
        }
    }

    override fun onDestroy() {
        unregisterScreenOffReceiver()
        removeCloseConfirmOverlay()
        removeFloatingOverlay()
        serviceScope.cancel()
        isRunning = false
        isPttMode = false
        activeThreadId = null
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignored
        }
        isRunning = false
        isPttMode = false
        activeThreadId = null
    }

    companion object {
        private const val TAG = "PrivateCallFgService"
        private const val CHANNEL_ID = "voxstage_private_call"
        private const val LOCK_SCREEN_CHANNEL_ID = "voxstage_lock_screen_ptt_v2"
        private const val NOTIFICATION_ID = 49_201
        private const val ACTION_START_CALL = "com.example.media.ACTION_START_PRIVATE_CALL"
        private const val ACTION_START_PTT = "com.example.media.ACTION_START_LIVE_PTT"
        private const val ACTION_TOGGLE_PTT = "com.example.media.ACTION_TOGGLE_LIVE_PTT"
        private const val ACTION_HIDE_FLOATING_PTT = "com.example.media.ACTION_HIDE_FLOATING_PTT"
        private const val ACTION_END_CALL = "com.example.media.ACTION_END_PRIVATE_CALL"
        private const val EXTRA_THREAD_ID = "extra_call_thread_id"
        private const val EXTRA_PEER_NAME = "extra_call_peer_name"
        private const val EXTRA_IS_PTT_MODE = "extra_is_ptt_mode"
        private const val EXTRA_PTT_TRANSMITTING = "extra_ptt_transmitting"
        private const val EXTRA_SHOW_FLOATING_OVERLAY = "extra_show_floating_overlay"

        private const val COLOR_STANDBY_EMERALD = 0xFF10B981.toInt()
        private const val COLOR_LIVE_CORAL = 0xFFFF4D6D.toInt()
        private const val COLOR_INCOMING_BLUE = 0xFF0088FF.toInt()
        private const val COLOR_CONNECTING_SLATE = 0xFF475569.toInt()
        private const val COLOR_WHITE_BORDER = 0xCCFFFFFF.toInt()

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPttMode: Boolean = false
            private set

        @Volatile
        var activeThreadId: String? = null
            private set

        private val _endCallEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val endCallEvents: SharedFlow<String> = _endCallEvents.asSharedFlow()

        fun ensureCallChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_call_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notif_channel_call_desc)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
                if (manager.getNotificationChannel(LOCK_SCREEN_CHANNEL_ID) == null) {
                    val lockChannel = NotificationChannel(
                        LOCK_SCREEN_CHANNEL_ID,
                        "Lock-Screen Push-to-Talk",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Shows quick Push-to-Talk controls on your lock screen."
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(lockChannel)
                }
            }
        }

        fun start(context: Context, threadId: String, peerName: String) {
            try {
                val appContext = context.applicationContext
                ensureCallChannel(appContext)
                val intent = Intent(appContext, PrivateCallForegroundService::class.java).apply {
                    action = ACTION_START_CALL
                    putExtra(EXTRA_THREAD_ID, threadId)
                    putExtra(EXTRA_PEER_NAME, peerName)
                    putExtra(EXTRA_IS_PTT_MODE, false)
                    putExtra(EXTRA_SHOW_FLOATING_OVERLAY, false)
                }
                ContextCompat.startForegroundService(appContext, intent)
            } catch (err: Exception) {
                Log.w(TAG, "Failed to start PrivateCallForegroundService", err)
            }
        }

        fun startPtt(
            context: Context,
            threadId: String,
            peerName: String,
            isTransmitting: Boolean,
            showFloatingOverlay: Boolean
        ) {
            try {
                val appContext = context.applicationContext
                ensureCallChannel(appContext)
                val intent = Intent(appContext, PrivateCallForegroundService::class.java).apply {
                    action = ACTION_START_PTT
                    putExtra(EXTRA_THREAD_ID, threadId)
                    putExtra(EXTRA_PEER_NAME, peerName)
                    putExtra(EXTRA_IS_PTT_MODE, true)
                    putExtra(EXTRA_PTT_TRANSMITTING, isTransmitting)
                    putExtra(EXTRA_SHOW_FLOATING_OVERLAY, showFloatingOverlay)
                }
                ContextCompat.startForegroundService(appContext, intent)
            } catch (err: Exception) {
                Log.w(TAG, "Failed to start Live PTT foreground service", err)
            }
        }

        fun hideFloatingPttOverlay(context: Context) {
            if (!isRunning) return
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, PrivateCallForegroundService::class.java).apply {
                    action = ACTION_HIDE_FLOATING_PTT
                }
                appContext.startService(intent)
            } catch (err: Exception) {
                Log.w(TAG, "Failed to hide floating PTT overlay", err)
            }
        }

        fun stop(context: Context) {
            try {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, PrivateCallForegroundService::class.java))
            } catch (err: Exception) {
                Log.w(TAG, "Failed to stop PrivateCallForegroundService", err)
            }
            isRunning = false
            isPttMode = false
            activeThreadId = null
        }
    }
}
