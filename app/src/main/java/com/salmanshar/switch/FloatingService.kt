package com.salmanshar.switch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var targetPkg = ""
    private var dsPkg = "com.deepseek.chat"
    private var txPkg = "com.termux"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        val prefs = getSharedPreferences("switch", MODE_PRIVATE)
        dsPkg = prefs.getString("ds", "com.deepseek.chat")!!
        txPkg = prefs.getString("tx", "com.termux")!!
        targetPkg = txPkg
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        view = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val p = (56 * resources.displayMetrics.density).toInt()
            width = p; height = p
            setOnClickListener { doSwitch() }
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20; y = 240
        }
        wm.addView(view, params)
        updateIcon()
        handler.post(poll)
    }

    private val poll = object : Runnable {
        override fun run() {
            val fg = foreground()
            if (fg == dsPkg) targetPkg = txPkg
            else if (fg == txPkg) targetPkg = dsPkg
            updateIcon()
            handler.postDelayed(this, 600)
        }
    }

    private fun updateIcon() {
        if (targetPkg == txPkg) {
            view.text = "T"
            view.setBackgroundColor(0xFF00C853.toInt())
        } else {
            view.text = "D"
            view.setBackgroundColor(0xFF2962FF.toInt())
        }
    }

    private fun foreground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 60000, end)
        val e = UsageEvents.Event()
        var pkg: String? = null
        var ts = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val isFg = if (Build.VERSION.SDK_INT >= 29) e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                       else @Suppress("DEPRECATION") e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isFg && e.timeStamp > ts) { ts = e.timeStamp; pkg = e.packageName }
        }
        return pkg
    }

    private fun doSwitch() {
        val i = packageManager.getLaunchIntentForPackage(targetPkg) ?: return
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
        runCatching { wm.removeView(view) }
        super.onDestroy()
    }
}
