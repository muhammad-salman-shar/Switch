package com.salmanshar.appswitch

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var repo: ConfigRepository
    private val holders = mutableListOf<Holder>()
    private val handler = Handler(Looper.getMainLooper())
    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { rebuild() }
    }

    class Holder(
        val config: ButtonConfig,
        val view: TextView,
        val params: WindowManager.LayoutParams,
    ) {
        var cycleIndex = 0
        val expanderViews = mutableListOf<TextView>()
        var isExpanded = false
        val miniViews = mutableListOf<TextView>()
        var timerActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repo = ConfigRepository(this)
        ContextCompat.registerReceiver(this, reloadReceiver,
            IntentFilter(ACTION_RELOAD), ContextCompat.RECEIVER_NOT_EXPORTED)
        rebuild()
        handler.post(poll)
    }

    private fun rebuild() {
        holders.forEach { h ->
            clearMinis(h)
            h.expanderViews.forEach { runCatching { wm.removeView(it) } }
            runCatching { wm.removeView(h.view) }
        }
        holders.clear()
        repo.loadButtons().forEach { addHolder(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHolder(cfg: ButtonConfig) {
        val view = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cfg.posX; y = cfg.posY
        }
        val holder = Holder(cfg, view, params)
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragged = false; var downTime = 0L
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    dragged = false; downTime = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 15f || abs(dy) > 15f) dragged = true
                    if (dragged) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        wm.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val held = System.currentTimeMillis() - downTime
                    if (!dragged) {
                        if (cfg.type == 0) {
                            if (held > 600) stopTimer(holder) else startTimer(holder)
                        } else onTap(holder)
                    } else {
                        cfg.posX = params.x; cfg.posY = params.y; repo.updateButton(cfg)
                        if (cfg.type == 0 && holder.timerActive) repositionMinis(holder)
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        holders.add(holder)
        applyVisual(holder)
    }

    private fun clearMinis(h: Holder) {
        h.miniViews.forEach { runCatching { wm.removeView(it) } }
        h.miniViews.clear()
    }

    private fun repositionMinis(h: Holder) {
        clearMinis(h)
        val ringSize = (h.config.sizeDp * resources.displayMetrics.density)
        val cx = h.params.x + ringSize / 2f
        val cy = h.params.y + ringSize / 2f
        val radius = ringSize * 0.28f
        val n = h.config.minis.size
        if (n == 0) return
        h.config.minis.forEachIndexed { i, mini ->
            val angle = 2.0 * Math.PI * i / n - Math.PI / 2
            val vx = (cx + radius * cos(angle) - mini.sizeDp * resources.displayMetrics.density / 2).toInt()
            val vy = (cy + radius * sin(angle) - mini.sizeDp * resources.displayMetrics.density / 2).toInt()
            val v = TextView(this).apply {
                val sp = (mini.sizeDp * resources.displayMetrics.density).toInt()
                width = sp; height = sp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(mini.color)
                }
                alpha = (mini.alpha / 100f)
            }
            val pp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = vx; y = vy
            }
            wm.addView(v, pp)
            h.miniViews.add(v)
        }
    }

    private fun startTimer(h: Holder) {
        stopTimer(h)
        h.timerActive = true
        repositionMinis(h)
        applyVisual(h)
        h.config.minis.forEachIndexed { idx, mini ->
            if (mini.role == "single") {
                handler.postDelayed({ fireMini(h, idx) }, mini.delayMs)
            } else {
                val interval = (mini.windowMs / mini.tapsCount.coerceAtLeast(1)).coerceAtLeast(1L)
                repeat(mini.tapsCount) { k ->
                    handler.postDelayed({ fireMini(h, idx) }, k * interval)
                }
            }
        }
    }

    private fun stopTimer(h: Holder) {
        h.timerActive = false
        clearMinis(h)
        applyVisual(h)
    }

    private fun fireMini(h: Holder, idx: Int) {
        val v = h.miniViews.getOrNull(idx) ?: return
        v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(60).start()
        }.start()
    }

    private fun applyVisual(h: Holder) {
        val p = (h.config.sizeDp * resources.displayMetrics.density).toInt()
        h.view.width = p; h.view.height = p; h.view.requestLayout()
        if (h.config.type == 0) {
            h.view.text = if (h.timerActive) "ON" else "T"
            h.view.setTextColor(Color.WHITE)
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((6 * resources.displayMetrics.density).toInt(),
                    if (h.timerActive) 0xFFFF1744.toInt() else 0xFF00E676.toInt())
            }
            return
        }
        val subs = h.config.subButtons
        if (subs.isEmpty()) return
        if (h.config.type == 4) {
            h.view.text = "4"
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF37474F.toInt())
            }
        } else {
            val idx = if (h.config.type == 1) 0 else h.cycleIndex % subs.size
            val sub = subs[idx]
            h.view.text = if (sub.pkg.isEmpty()) "?" else label(sub.pkg).take(1).uppercase().ifEmpty { "?" }
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(sub.color)
            }
        }
    }

    private fun onTap(h: Holder) {
        val cfg = h.config
        when (cfg.type) {
            1 -> launch(cfg.subButtons.getOrNull(0)?.pkg)
            4 -> toggleExpander(h)
            else -> {
                val idx = h.cycleIndex % cfg.subButtons.size
                launch(cfg.subButtons[idx].pkg)
                h.cycleIndex = (idx + 1) % cfg.subButtons.size
                handler.postDelayed({ applyVisual(h) }, 200)
            }
        }
    }

    private fun toggleExpander(h: Holder) {
        if (h.isExpanded) {
            h.expanderViews.forEach { runCatching { wm.removeView(it) } }
            h.expanderViews.clear(); h.isExpanded = false
            return
        }
        val subs = h.config.subButtons
        val mainSize = h.config.sizeDp * resources.displayMetrics.density
        val radius = mainSize * 1.6f
        subs.forEachIndexed { i, sub ->
            val angle = (2.0 * Math.PI * i / subs.size) - Math.PI / 2
            val cx = h.params.x + (radius * cos(angle)).toInt() + mainSize.toInt() / 2
            val cy = h.params.y + (radius * sin(angle)).toInt() + mainSize.toInt() / 2
            val v = TextView(this).apply {
                text = if (sub.pkg.isEmpty()) "?" else label(sub.pkg).take(1).uppercase().ifEmpty { "?" }
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val sp = (sub.sizeDp * resources.displayMetrics.density).toInt()
                width = sp; height = sp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(sub.color)
                }
                setOnClickListener { launch(sub.pkg) }
            }
            val pp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = cx; y = cy
            }
            wm.addView(v, pp)
            h.expanderViews.add(v)
        }
        h.isExpanded = true
    }

    private val poll = object : Runnable {
        override fun run() {
            val fg = foreground()
            holders.forEach { h ->
                if (h.config.type in 2..5 && h.config.type != 4) {
                    val idx = h.config.subButtons.indexOfFirst { it.pkg == fg }
                    if (idx >= 0) h.cycleIndex = (idx + 1) % h.config.subButtons.size
                }
                if (h.config.type != 0) applyVisual(h)
            }
            handler.postDelayed(this, 700)
        }
    }

    private fun label(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) { "" }

    private fun foreground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 60000, end)
        val e = UsageEvents.Event()
        var pkg: String? = null; var ts = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val isFg = if (Build.VERSION.SDK_INT >= 29) e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                       else @Suppress("DEPRECATION") e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isFg && e.timeStamp > ts) { ts = e.timeStamp; pkg = e.packageName }
        }
        return pkg
    }

    private fun launch(pkg: String?) {
        if (pkg.isNullOrEmpty()) return
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("sw", "Switch", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, "sw")
            .setContentTitle("Switch running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        runCatching { unregisterReceiver(reloadReceiver) }
        holders.forEach { h ->
            clearMinis(h)
            h.expanderViews.forEach { runCatching { wm.removeView(it) } }
            runCatching { wm.removeView(h.view) }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RELOAD = "com.salmanshar.appswitch.RELOAD"
    }
}
