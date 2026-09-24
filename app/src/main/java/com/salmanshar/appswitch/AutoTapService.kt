package com.salmanshar.appswitch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoTapService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { Log.d("SwitchTap", "gesture completed ($x,$y)") }
                override fun onCancelled(g: GestureDescription?) { Log.d("SwitchTap", "gesture cancelled ($x,$y)") }
            }, Handler(Looper.getMainLooper()))
            Log.d("SwitchTap", "dispatchGesture($x,$y) ok=$ok")
            ok
        } catch (e: Exception) {
            Log.e("SwitchTap", "tap threw", e)
            false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("SwitchTap", "service connected")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.d("SwitchTap", "service destroyed")
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AutoTapService? = null
    }
}
