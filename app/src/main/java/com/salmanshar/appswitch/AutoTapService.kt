package com.salmanshar.appswitch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoTapService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float, durationMs: Long = 120L): Boolean {
        val hit = findNodeAt(x.toInt(), y.toInt())
        if (hit != null) {
            val clickable = climbToClickable(hit)
            if (clickable != null) {
                val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                push("NODE-CLICK ${clickable.packageName} ok=$ok")
                if (ok) return true
            } else {
                push("node found but no clickable ancestor at ($x,$y)")
            }
        } else {
            push("no node found at ($x,$y) — falling back to gesture")
        }
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { push("gesture COMPLETED ($x,$y)") }
                override fun onCancelled(g: GestureDescription?) { push("gesture CANCELLED ($x,$y)") }
            }, Handler(Looper.getMainLooper()))
            push("gesture dispatch($x,$y) returned=$ok")
            ok
        } catch (e: Exception) {
            push("gesture EXCEPTION: ${e.message}")
            false
        }
    }

    private fun findNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: run { push("root=null"); return null }
        return search(root, x, y, 0)
    }

    private fun search(node: AccessibilityNodeInfo?, x: Int, y: Int, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.contains(x, y)) {
            for (i in 0 until node.childCount) {
                val c = search(node.getChild(i), x, y, depth + 1)
                if (c != null) return c
            }
            return node
        }
        return null
    }

    private fun climbToClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var n = node
        var d = 0
        while (n != null && d < 12) {
            if (n.isClickable) return n
            n = n.parent
            d++
        }
        return null
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
        private const val MAX = 80

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
