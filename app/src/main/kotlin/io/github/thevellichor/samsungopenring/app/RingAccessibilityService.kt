package io.github.thevellichor.samsungopenring.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class RingAccessibilityService : AccessibilityService() {

    companion object {
        private const val PREFS_NAME = "openring_prefs"

        const val KEY_SCROLL_DISTANCE = "scroll_distance"
        const val KEY_SWIPE_DURATION = "swipe_duration"
        const val KEY_INPUT_COOLDOWN = "input_cooldown"

        const val DEFAULT_SCROLL_DISTANCE = 30
        const val DEFAULT_SWIPE_DURATION = 150
        const val DEFAULT_INPUT_COOLDOWN = 300

        @Volatile
        private var instance: RingAccessibilityService? = null

        @Volatile
        private var lastSwipeTime = 0L

        fun swipeUp(): Boolean {
            val service = instance ?: return false

            val prefs = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

            val distancePercent = prefs.getInt(
                KEY_SCROLL_DISTANCE,
                DEFAULT_SCROLL_DISTANCE
            ).coerceIn(10, 60)

            val duration = prefs.getInt(
                KEY_SWIPE_DURATION,
                DEFAULT_SWIPE_DURATION
            ).coerceIn(80, 400)

            val cooldown = prefs.getInt(
                KEY_INPUT_COOLDOWN,
                DEFAULT_INPUT_COOLDOWN
            ).coerceIn(100, 1000)

            val now = SystemClock.elapsedRealtime()

            if (now - lastSwipeTime < cooldown) {
                return false
            }

            val width = service.resources.displayMetrics.widthPixels.toFloat()
            val height = service.resources.displayMetrics.heightPixels.toFloat()

            val distance = height * (distancePercent / 100f)

            // Start around the lower-middle of the screen.
            val startY = height * 0.70f

            // Move upward by the configured distance.
            val endY = (startY - distance).coerceAtLeast(height * 0.10f)

            val path = Path().apply {
                moveTo(width * 0.5f, startY)
                lineTo(width * 0.5f, endY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        duration.toLong()
                    )
                )
                .build()

            val started = service.dispatchGesture(gesture, null, null)

            if (started) {
                lastSwipeTime = now
            }

            return started
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No accessibility-event inspection is required.
    }

    override fun onInterrupt() {
    }
}
