package io.github.thevellichor.samsungopenring.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class RingAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: RingAccessibilityService? = null

        fun swipeUp(): Boolean {
            val service = instance ?: return false

            val width = service.resources.displayMetrics.widthPixels.toFloat()
            val height = service.resources.displayMetrics.heightPixels.toFloat()

            val path = Path().apply {
                moveTo(width * 0.5f, height * 0.78f)
                lineTo(width * 0.5f, height * 0.25f)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        300
                    )
                )
                .build()

            return service.dispatchGesture(gesture, null, null)
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
