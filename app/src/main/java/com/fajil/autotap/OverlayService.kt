package com.fajil.autotap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Runs a small draggable floating toggle button. When toggled ON, a
 * transparent full-screen window captures touches: press-and-hold
 * anywhere triggers a repeating auto double-tap at that spot (following
 * the finger if it moves) until the finger is lifted. A quick tap
 * (released before the hold threshold) is replayed as a normal single
 * tap so the phone still works normally for short taps.
 */
class OverlayService : Service() {

    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "autotap_channel"
        private const val NOTIFICATION_ID = 1

        // Time held before auto double-tapping kicks in.
        private const val LONG_PRESS_THRESHOLD_MS = 350L
        // Gap between each repeated double-tap while held.
        private const val REPEAT_INTERVAL_MS = 250L
    }

    private lateinit var windowManager: WindowManager
    private var toggleButton: View? = null
    private var captureOverlay: View? = null
    private var captureEnabled = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isHolding = false
    private var tapLoopStarted = false
    private var holdX = 0f
    private var holdY = 0f
    private var longPressRunnable: Runnable? = null
    private var tapLoopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        addToggleButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cancelHoldLoop()
        removeCaptureOverlay()
        toggleButton?.let { runCatching { windowManager.removeView(it) } }
        toggleButton = null
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Auto Tap Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Tap")
            .setContentText("Tap the floating button to toggle auto double-tap mode")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ---------- Floating draggable toggle button ----------

    private fun addToggleButton() {
        val button = View(this)
        val size = (56 * resources.displayMetrics.density).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleCaptureMode()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(button, params)
        toggleButton = button
        updateToggleAppearance()
    }

    private fun updateToggleAppearance() {
        toggleButton?.setBackgroundColor(
            if (captureEnabled) Color.parseColor("#CC4CAF50") else Color.parseColor("#CC2196F3")
        )
    }

    private fun toggleCaptureMode() {
        captureEnabled = !captureEnabled
        updateToggleAppearance()
        if (captureEnabled) addCaptureOverlay() else removeCaptureOverlay()
    }

    // ---------- Full-screen touch-capturing overlay (only while capture mode is on) ----------

    private fun addCaptureOverlay() {
        if (captureOverlay != null) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val overlay = FrameLayout(this)
        overlay.setOnTouchListener { _, event -> handleCaptureTouch(event) }

        windowManager.addView(overlay, params)
        captureOverlay = overlay
    }

    private fun removeCaptureOverlay() {
        captureOverlay?.let { runCatching { windowManager.removeView(it) } }
        captureOverlay = null
        cancelHoldLoop()
    }

    private fun handleCaptureTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                holdX = event.rawX
                holdY = event.rawY
                isHolding = true
                tapLoopStarted = false
                scheduleLongPress()
            }
            MotionEvent.ACTION_MOVE -> {
                holdX = event.rawX
                holdY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val wasLongPress = tapLoopStarted
                isHolding = false
                cancelHoldLoop()
                if (!wasLongPress) {
                    // Short tap: replay it as a normal single tap so the phone still works.
                    TapAccessibilityService.instance?.performSingleTap(holdX, holdY) {}
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isHolding = false
                cancelHoldLoop()
            }
        }
        return true
    }

    private fun scheduleLongPress() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (isHolding) {
                tapLoopStarted = true
                startTapLoop()
            }
        }
        longPressRunnable = runnable
        mainHandler.postDelayed(runnable, LONG_PRESS_THRESHOLD_MS)
    }

    private fun startTapLoop() {
        val service = TapAccessibilityService.instance ?: return
        fireTap(service)
    }

    private fun fireTap(service: TapAccessibilityService) {
        if (!isHolding) return
        service.performDoubleTap(holdX, holdY) {
            if (isHolding) {
                val next = Runnable { if (isHolding) fireTap(service) }
                tapLoopRunnable = next
                mainHandler.postDelayed(next, REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun cancelHoldLoop() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        tapLoopRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
        tapLoopRunnable = null
        tapLoopStarted = false
    }
}
