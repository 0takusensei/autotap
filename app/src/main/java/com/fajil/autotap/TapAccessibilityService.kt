package com.fajil.autotap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that injects real, system-level tap gestures.
 * OverlayService reads finger position from a transparent touch-capturing
 * window and asks this service to fire the actual taps, since only an
 * AccessibilityService is allowed to dispatch gestures to other apps.
 */
class TapAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TapAccessibilityService? = null
            private set

        private const val TAP_DURATION_MS = 40L
        private const val DOUBLE_TAP_GAP_MS = 60L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /** Fires a single tap at (x, y). Used for quick, non-held taps so normal use still works. */
    fun performSingleTap(x: Float, y: Float, onDone: () -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, doneCallback(onDone), null)
    }

    /** Fires two quick taps (a double-tap) at (x, y). Used while the user is holding. */
    fun performDoubleTap(x: Float, y: Float, onDone: () -> Unit) {
        val path1 = Path().apply { moveTo(x, y) }
        val path2 = Path().apply { moveTo(x, y) }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, TAP_DURATION_MS)
        val stroke2 = GestureDescription.StrokeDescription(
            path2,
            TAP_DURATION_MS + DOUBLE_TAP_GAP_MS,
            TAP_DURATION_MS
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, doneCallback(onDone), null)
    }

    private fun doneCallback(onDone: () -> Unit) = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
        override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
    }
}
