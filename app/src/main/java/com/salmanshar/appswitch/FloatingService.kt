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
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository
import kotlin.math.abs

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
        val childViews = mutableListOf<TextView>()
        var isExpanded = false
        val miniViews = mutableListOf<TextView>()
        var timerActive = false
        var longPressRunnable: Runnable? = null
        val panelViews = mutableListOf<TextView>()
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
            clearMinis(h); clearChildren(h); clearPanel(h)
            runCatching { wm.removeView(h.view) }
        }
        holders.clear()
        repo.loadButtons().forEach { addHolder(it) }
    }

    private fun openEditor(cfg: ButtonConfig) {
        val target = if (cfg.type == 0) EditTimerActivity::class.java else EditButtonActivity::class.java
        val i = Intent(this, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("id", cfg.id)
        }
        startActivity(i)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHolder(cfg: ButtonConfig) {
        val view = TextView(this).apply {
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
        var dragged = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    dragged = false
                    holder.longPressRunnable = Runnable {
                        if (!dragged) onLongPress(holder)
                    }.also { handler.postDelayed(it, 2000) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 15f || abs(dy) > 15f) {
                        dragged = true
                        holder.longPressRunnable?.let { handler.removeCallbacks(it) }
                        holder.longPressRunnable = null
                    }
                    if (dragged) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        wm.updateViewLayout(view, params)
                        if (cfg.type == 4 && holder.isExpanded) layoutChildren(holder)
                        if (cfg.type == 0 && holder.timerActive) layoutMinis(holder)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    holder.longPressRunnable?.let { handler.removeCallbacks(it) }
                    holder.longPressRunnable = null
                    if (!dragged) onTap(holder)
                    else {
                        cfg.posX = params.x; cfg.posY = params.y; repo.updateButton(cfg)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    holder.longPressRunnable?.let { handler.removeCallbacks(it) }
                    holder.longPressRunnable = null
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        holders.add(holder)
        applyVisual(holder)
    }

    private fun onLongPress(h: Holder) {
        if (h.config.type == 0) showTimerPanel(h) else openEditor(h.config)
    }

    private fun clearChildren(h: Holder) {
        h.childViews.forEach { runCatching { wm.removeView(it) } }
        h.childViews.clear()
        h.isExpanded = false
    }

    private fun clearMinis(h: Holder) {
        h.miniViews.forEach { runCatching { wm.removeView(it) } }
        h.miniViews.clear()
    }

    private fun clearPanel(h: Holder) {
        h.panelViews.forEach { runCatching { wm.removeView(it) } }
        h.panelViews.clear()
    }

    private fun layoutChildren(h: Holder) {
        h.childViews.forEachIndexed { i, v ->
            val pp = v.tag as? WindowManager.LayoutParams ?: return@forEachIndexed
            val mainSize = (h.config.sizeDp * resources.displayMetrics.density).toInt()
            pp.x = h.params.x + mainSize + (12 * resources.displayMetrics.density).toInt()
            pp.y = h.params.y + i * ((h.config.subButtons.getOrNull(i)?.sizeDp ?: 45) * resources.displayMetrics.density).toInt() +
                   i * (8 * resources.displayMetrics.density).toInt()
            wm.updateViewLayout(v, pp)
        }
    }

    private fun layoutMinis(h: Holder) {
        val ringSize = h.config.sizeDp * resources.displayMetrics.density
        val cx = h.params.x + ringSize / 2f
        val cy = h.params.y + ringSize / 2f
        val radius = ringSize * 0.32f
        val n = h.config.minis.size
        if (n == 0) return
        h.miniViews.forEachIndexed { i, v ->
            val mini = h.config.minis[i]
            val angle = 2.0 * Math.PI * i / n - Math.PI / 2
            val pp = v.tag as? WindowManager.LayoutParams ?: return@forEachIndexed
            pp.x = (cx + radius * Math.cos(angle) - mini.sizeDp * resources.displayMetrics.density / 2).toInt()
            pp.y = (cy + radius * Math.sin(angle) - mini.sizeDp * resources.displayMetrics.density / 2).toInt()
            wm.updateViewLayout(v, pp)
        }
    }

    private fun spawnMinis(h: Holder) {
        clearMinis(h)
        val n = h.config.minis.size
        if (n == 0) return
        h.config.minis.forEachIndexed { i, mini ->
            val v = TextView(this).apply {
                val sp = (mini.sizeDp * resources.displayMetrics.density).toInt()
                width = sp; height = sp
                text = mini.name
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * 0.45f)
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
            ).apply { gravity = Gravity.TOP or Gravity.START }
            v.tag = pp
            wm.addView(v, pp)
            h.miniViews.add(v)
        }
        layoutMinis(h)
    }

    private fun showTimerPanel(h: Holder) {
        if (h.panelViews.isNotEmpty()) { clearPanel(h); return }
        val base = h.config.sizeDp * resources.displayMetrics.density
        val panelSize = (36 * resources.displayMetrics.density).toInt()
        val gap = (10 * resources.displayMetrics.density).toInt()
        val items = listOf("+" to { addMiniQuick(h) }, "⚙" to { openEditor(h.config); clearPanel(h) }, "✕" to { clearPanel(h) })
        items.forEachIndexed { i, (label, action) ->
            val v = TextView(this).apply {
                text = label
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                width = panelSize; height = panelSize
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF37474F.toInt())
                }
                setOnClickListener { action() }
            }
            val pp = WindowManager.LayoutParams(
                panelSize, panelSize,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = h.params.x - (i + 1) * (panelSize + gap)
                y = h.params.y + (base / 2).toInt() - panelSize / 2
            }
            wm.addView(v, pp)
            h.panelViews.add(v)
        }
    }

    private fun addMiniQuick(h: Holder) {
        if (h.config.minis.size >= 6) return
        val next = (h.config.minis.maxOfOrNull { it.delayMs } ?: 0L) + 500L
        h.config.minis.add(com.salmanshar.appswitch.model.MiniTimer(delayMs = next, name = "m${h.config.minis.size + 1}"))
        repo.updateButton(h.config)
        clearPanel(h)
        if (h.timerActive) { spawnMinis(h) }
    }

    private fun applyVisual(h: Holder) {
        val p = (h.config.sizeDp * resources.displayMetrics.density).toInt()
        h.view.width = p; h.view.height = p; h.view.requestLayout()
        h.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, p * 0.4f)
        val cfg = h.config
        if (cfg.type == 0) {
            val nm = cfg.subButtons.getOrNull(0)?.name ?: ""
            h.view.text = nm
            h.view.setTextColor(Color.WHITE)
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * resources.displayMetrics.density).toInt(),
                    if (h.timerActive) 0xFFFF1744.toInt() else 0xFF00E676.toInt())
            }
            return
        }
        val subs = cfg.subButtons
        if (subs.isEmpty()) return
        if (cfg.type == 4) {
            h.view.text = "4"
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF37474F.toInt())
            }
        } else {
            val idx = if (cfg.type == 1) 0 else h.cycleIndex % subs.size
            val sub = subs[idx]
            h.view.text = displayName(sub)
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(sub.color)
            }
        }
    }

    private fun displayName(sub: com.salmanshar.appswitch.model.SubButton): String {
        if (sub.name.isNotEmpty()) return sub.name
        if (sub.pkg.isEmpty()) return "?"
        return label(sub.pkg).take(1).uppercase().ifEmpty { "?" }
    }

    private fun onTap(h: Holder) {
        val cfg = h.config
        when (cfg.type) {
            0 -> toggleTimer(h)
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

    private fun toggleTimer(h: Holder) {
        if (h.timerActive) {
            stopTimer(h)
        } else {
            h.timerActive = true
            applyVisual(h)
            spawnMinis(h)
            val now = System.currentTimeMillis()
            h.config.minis.forEachIndexed { idx, mini ->
                val target = now + mini.delayMs
                if (mini.role == "single") {
                    val d = (target - System.currentTimeMillis()).coerceAtLeast(0L)
                    handler.postDelayed({ fireMini(h, idx) }, d)
                } else {
                    val interval = (mini.windowMs / mini.tapsCount.coerceAtLeast(1)).coerceAtLeast(1L)
                    repeat(mini.tapsCount) { k ->
                        val t = target + k * interval
                        val d = (t - System.currentTimeMillis()).coerceAtLeast(0L)
                        handler.postDelayed({ fireMini(h, idx) }, d)
                    }
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
        v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(50).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
        }.start()
    }

    private fun toggleExpander(h: Holder) {
        if (h.isExpanded) { clearChildren(h); return }
        val subs = h.config.subButtons
        val mainSize = (h.config.sizeDp * resources.displayMetrics.density).toInt()
        subs.forEachIndexed { i, sub ->
            val sp = (sub.sizeDp * resources.displayMetrics.density).toInt()
            val v = TextView(this).apply {
                text = displayName(sub)
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                width = sp; height = sp
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * 0.4f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(sub.color)
                }
                setOnClickListener { launch(sub.pkg) }
                setOnLongClickListener { openEditor(h.config); true }
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
                x = h.params.x + mainSize + (12 * resources.displayMetrics.density).toInt()
                y = h.params.y + i * (sp + (8 * resources.displayMetrics.density).toInt())
            }
            v.tag = pp
            wm.addView(v, pp)
            h.childViews.add(v)
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
            clearMinis(h); clearChildren(h); clearPanel(h)
            runCatching { wm.removeView(h.view) }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RELOAD = "com.salmanshar.appswitch.RELOAD"
    }
}
