package com.salmanshar.appswitch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AutoTapService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float, durationMs: Long = 120L): Boolean {
        val msg: String
        val ok: Boolean
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { push("onCompleted ($x,$y)") }
                override fun onCancelled(g: GestureDescription?) { push("onCancelled ($x,$y)") }
            }, Handler(Looper.getMainLooper()))
            msg = "dispatch($x,$y) dur=${durationMs}ms returned=$ok"
        } catch (e: Exception) {
            push("EXCEPTION: ${e.message}")
            return false
        }
        push(msg)
        return ok
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        push("service CONNECTED")
    }

    override fun onDestroy() {
        push("service DESTROYED")
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AutoTapService? = null
        private val buffer = ArrayDeque<String>()
        private const val MAX = 40

        fun push(s: String) {
            synchronized(buffer) {
                buffer.addLast("${System.currentTimeMillis() % 100000}  $s")
                while (buffer.size > MAX) buffer.removeFirst()
            }
        }

        fun dump(): List<String> = synchronized(buffer) { buffer.toList() }
        fun clear() = synchronized(buffer) { buffer.clear() }
    }
}
