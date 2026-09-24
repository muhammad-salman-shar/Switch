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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository
import com.salmanshar.appswitch.model.MiniTimer
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
        val childViews = mutableListOf<TextView>()
        var isExpanded = false
        val miniViews = mutableListOf<TextView>()
        var timerActive = false
        var longPressRunnable: Runnable? = null
        val menuViews = mutableListOf<TextView>()
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
            clearMenu(h); clearMinis(h); clearChildren(h)
            runCatching { wm.removeView(h.view) }
        }
        holders.clear()
        repo.loadButtons().forEach { addHolder(it) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun openEditor(cfg: ButtonConfig, miniIndex: Int = -1) {
        val target = when {
            cfg.type == 0 && miniIndex >= 0 -> EditMiniActivity::class.java
            cfg.type == 0 -> EditTimerActivity::class.java
            else -> EditButtonActivity::class.java
        }
        val i = Intent(this, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("id", cfg.id)
            if (miniIndex >= 0) putExtra("miniIndex", miniIndex)
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
            overlayType(),
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
                    holder.longPressRunnable = Runnable { if (!dragged) onLongPress(holder) }
                        .also { handler.postDelayed(it, 800) }
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
                    else { cfg.posX = params.x; cfg.posY = params.y; repo.updateButton(cfg) }
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
        if (h.config.type == 0) {
            showMenu(h, listOf(
                "Edit" to { openEditor(h.config) },
                "Delete" to {
                    repo.deleteButton(h.config.id)
                    rebuild()
                },
                "Add" to { addMiniQuick(h) },
            ))
        } else {
            openEditor(h.config)
        }
    }

    private fun clearMenu(h: Holder) {
        h.menuViews.forEach { runCatching { wm.removeView(it) } }
        h.menuViews.clear()
    }

    private fun showMenu(h: Holder, items: List<Pair<String, () -> Unit>>) {
        clearMenu(h)
        val dens = resources.displayMetrics.density
        val itemH = (44 * dens).toInt()
        val menuW = (170 * dens).toInt()
        val startX = h.params.x
        val startY = h.params.y + (h.config.sizeDp * dens).toInt() + (8 * dens).toInt()
        items.forEachIndexed { i, (label, action) ->
            val v = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding((16 * dens).toInt(), 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(0xFF37474F.toInt())
                    cornerRadius = 10f * dens
                }
                isClickable = true
                setOnClickListener {
                    clearMenu(h)
                    action()
                }
            }
            val pp = WindowManager.LayoutParams(
                menuW, itemH, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = startX
                y = startY + i * (itemH + (6 * dens).toInt())
            }
            wm.addView(v, pp)
            h.menuViews.add(v)
        }
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

    private fun layoutChildren(h: Holder) {
        val dens = resources.displayMetrics.density
        val mainSize = (h.config.sizeDp * dens).toInt()
        h.childViews.forEachIndexed { i, v ->
            val pp = v.tag as? WindowManager.LayoutParams ?: return@forEachIndexed
            val sp = (h.config.subButtons.getOrNull(i)?.sizeDp ?: 45) * dens
            pp.x = h.params.x + mainSize + (12 * dens).toInt()
            pp.y = h.params.y + (i * (sp + 8 * dens)).toInt()
            wm.updateViewLayout(v, pp)
        }
    }

    private fun layoutMinis(h: Holder) {
        val dens = resources.displayMetrics.density
        val ringSize = h.config.sizeDp * dens
        val cx = h.params.x + ringSize / 2f
        val cy = h.params.y + ringSize / 2f
        val radius = ringSize * 0.32f
        val n = h.config.minis.size
        if (n == 0) return
        h.miniViews.forEachIndexed { i, v ->
            val mini = h.config.minis.getOrNull(i) ?: return@forEachIndexed
            val angle = 2.0 * Math.PI * i / n - Math.PI / 2
            val pp = v.tag as? WindowManager.LayoutParams ?: return@forEachIndexed
            pp.x = (cx + radius * cos(angle) - mini.sizeDp * dens / 2).toInt()
            pp.y = (cy + radius * sin(angle) - mini.sizeDp * dens / 2).toInt()
            wm.updateViewLayout(v, pp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun spawnMinis(h: Holder) {
        clearMinis(h)
        val dens = resources.displayMetrics.density
        if (h.config.minis.isEmpty()) return
        h.config.minis.forEachIndexed { i, mini ->
            val sp = (mini.sizeDp * dens).toInt()
            val v = TextView(this).apply {
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
                alpha = mini.alpha / 100f
            }
            var miniDown = 0L
            var miniDragged = false
            var mDownX = 0f; var mDownY = 0f; var mStartX = 0; var mStartY = 0
            val pp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            v.tag = pp
            v.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        miniDown = System.currentTimeMillis()
                        miniDragged = false
                        mDownX = e.rawX; mDownY = e.rawY; mStartX = pp.x; mStartY = pp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - mDownX; val dy = e.rawY - mDownY
                        if (abs(dx) > 15f || abs(dy) > 15f) miniDragged = true
                        if (miniDragged) {
                            pp.x = mStartX + dx.toInt(); pp.y = mStartY + dy.toInt()
                            wm.updateViewLayout(v, pp)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val held = System.currentTimeMillis() - miniDown
                        if (!miniDragged && held > 700) {
                            showMiniMenu(h, i)
                        } else if (!miniDragged) {
                            fireMiniView(v)
                        }
                        true
                    }
                    else -> false
                }
            }
            wm.addView(v, pp)
            h.miniViews.add(v)
        }
        layoutMinis(h)
    }

    private fun showMiniMenu(h: Holder, idx: Int) {
        val dens = resources.displayMetrics.density
        val itemH = (44 * dens).toInt()
        val menuW = (150 * dens).toInt()
        val miniView = h.miniViews.getOrNull(idx) ?: return
        val pp = miniView.tag as? WindowManager.LayoutParams ?: return
        val items = listOf(
            "Edit" to { openEditor(h.config, idx) },
            "Delete" to {
                if (idx in h.config.minis.indices) {
                    h.config.minis.removeAt(idx)
                    repo.updateButton(h.config)
                    spawnMinis(h)
                }
            },
        )
        val existing = h.menuViews.toList()
        existing.forEach { runCatching { wm.removeView(it) } }
        h.menuViews.clear()
        items.forEachIndexed { i, (label, action) ->
            val v = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding((16 * dens).toInt(), 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(0xFF263238.toInt())
                    cornerRadius = 10f * dens
                }
                isClickable = true
                setOnClickListener {
                    h.menuViews.forEach { runCatching { wm.removeView(it) } }
                    h.menuViews.clear()
                    action()
                }
            }
            val mpp = WindowManager.LayoutParams(
                menuW, itemH, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = pp.x + pp.width + (4 * dens).toInt()
                y = pp.y + i * (itemH + (6 * dens).toInt())
            }
            wm.addView(v, mpp)
            h.menuViews.add(v)
        }
    }

    private fun fireMiniView(v: TextView) {
        v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(60).start()
        }.start()
    }

    private fun addMiniQuick(h: Holder) {
        if (h.config.minis.size >= 6) return
        val next = (h.config.minis.maxOfOrNull { it.delayMs } ?: -500L) + 500L
        h.config.minis.add(MiniTimer(
            delayMs = next.coerceIn(0L, 5000L),
            name = "m${h.config.minis.size + 1}",
        ))
        repo.updateButton(h.config)
        if (h.timerActive) spawnMinis(h)
    }

    private fun applyVisual(h: Holder) {
        val dens = resources.displayMetrics.density
        val p = (h.config.sizeDp * dens).toInt()
        h.view.width = p; h.view.height = p; h.view.requestLayout()
        h.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, p * 0.35f)
        h.view.alpha = h.config.alpha / 100f
        val cfg = h.config
        if (cfg.type == 0) {
            h.view.text = cfg.name
            h.view.setTextColor(Color.WHITE)
            h.view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * dens).toInt(),
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
            h.timerActive = false
            clearMinis(h)
            applyVisual(h)
            return
        }
        h.timerActive = true
        applyVisual(h)
        spawnMinis(h)
        h.config.minis.forEachIndexed { idx, mini ->
            val delay = mini.delayMs.coerceIn(0L, 5000L)
            handler.postDelayed({ fireMini(h, idx) }, delay)
        }
    }

    private fun fireMini(h: Holder, idx: Int) {
        val v = h.miniViews.getOrNull(idx) ?: return
        fireMiniView(v)
    }

    private fun toggleExpander(h: Holder) {
        if (h.isExpanded) { clearChildren(h); return }
        val dens = resources.displayMetrics.density
        val subs = h.config.subButtons
        val mainSize = (h.config.sizeDp * dens).toInt()
        subs.forEachIndexed { i, sub ->
            val sp = (sub.sizeDp * dens).toInt()
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
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = h.params.x + mainSize + (12 * dens).toInt()
                y = h.params.y + i * (sp + (8 * dens).toInt())
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
            clearMenu(h); clearMinis(h); clearChildren(h)
            runCatching { wm.removeView(h.view) }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RELOAD = "com.salmanshar.appswitch.RELOAD"
    }
}
