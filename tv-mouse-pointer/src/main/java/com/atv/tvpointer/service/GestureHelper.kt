package com.atv.tvpointer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

class GestureHelper(private val service: AccessibilityService) {

    private val tag = "GestureHelper"

    fun click(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(tag, "Click completed at ($x, $y)")
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(tag, "Click cancelled at ($x, $y)")
                    onComplete?.invoke(false)
                }
            },
            null
        )
    }

    fun doubleClick(x: Float, y: Float) {
        click(x, y) { success ->
            if (success) {
                // Short delay then second tap
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    click(x, y)
                }, 100)
            }
        }
    }

    fun longClick(x: Float, y: Float, durationMs: Long = 800L) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    fun scroll(x: Float, y: Float, deltaY: Float, durationMs: Long = 250L) {
        val startY = y
        val endY = y - deltaY // scroll down moves finger up, scroll up moves finger down

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 400L) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        service.dispatchGesture(gesture, null, null)
    }
}
