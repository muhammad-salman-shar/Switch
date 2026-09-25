package com.salmanshar.appswitch

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository

class MainActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var listLayout: LinearLayout
    private lateinit var status: TextView
    private var dialogOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ConfigRepository(this)
        prefs = getSharedPreferences("switch", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply { text = "Switch"; textSize = 22f })
        status = TextView(this).apply { textSize = 13f; setPadding(0, 12, 0, 12) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Grant Overlay"
            setOnClickListener { openOverlaySettings() }
        })
        root.addView(Button(this).apply {
            text = "Grant Usage Access"
            setOnClickListener { openUsageSettings() }
        })
        root.addView(Button(this).apply {
            text = "Grant Accessibility (auto-tap)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Start Floating"
            setOnClickListener {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, FloatingService::class.java))
            }
        })
        root.addView(Button(this).apply {
            text = "Stop Floating"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingService::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "DEBUG: Test Tap (center)"
            setOnClickListener {
                val svc = AutoTapService.instance
                if (svc == null) {
                    AlertDialog.Builder(this@MainActivity).setMessage("Accessibility OFF").show()
                    return@setOnClickListener
                }
                val dm = resources.displayMetrics
                val cx = dm.widthPixels / 2f
                val cy = dm.heightPixels / 2f
                AutoTapService.push("MANUAL test tap center=($cx,$cy)")
                svc.tap(cx, cy, 120L)
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Tap bheja: ($cx, $cy)\n\nAb Chrome me jaake dekho ki scroll hua ya kisi cheez pe click hua.")
                    .setPositiveButton("OK", null).show()
            }
        })
        root.addView(Button(this).apply {
            text = "+  Add Button"
            setOnClickListener { showAddDialog() }
        })
        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }
        root.addView(ScrollView(this).apply { addView(listLayout) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.post { maybePrompt() }
    }

    override fun onResume() { super.onResume(); refresh(); rootPromptCheck() }

    private fun rootPromptCheck() { listLayout.postDelayed({ maybePrompt() }, 250) }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val ux = hasUsageAccess()
        val ax = AutoTapService.instance != null
        status.text = "Overlay: ${if (ov) "OK" else "MISSING"}   Usage: ${if (ux) "OK" else "MISSING"}   AutoTap: ${if (ax) "OK" else "MISSING"}"
        listLayout.removeAllViews()
        val buttons = repo.loadButtons()
        if (buttons.isEmpty()) listLayout.addView(TextView(this).apply { text = "Koi button nahi. Plus se add karo." })
        buttons.forEachIndexed { i, cfg ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            row.addView(TextView(this).apply {
                text = "#${i + 1}  ${typeName(cfg.type)}"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    val target = if (cfg.type == 0) EditTimerActivity::class.java else EditButtonActivity::class.java
                    val it2 = Intent(this@MainActivity, target)
                    it2.putExtra("id", cfg.id)
                    startActivity(it2)
                }
            })
            row.addView(Button(this).apply {
                text = "X"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Delete button?")
                        .setPositiveButton("Delete") { _, _ ->
                            repo.deleteButton(cfg.id); sendReload(); refresh()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            })
            listLayout.addView(row)
        }
    }

    private fun maybePrompt() {
        if (dialogOpen) return
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = hasUsageAccess()
        val overlayLater = prefs.getBoolean("laterOverlay", false)
        val usageLater = prefs.getBoolean("laterUsage", false)

        if (!overlayOk && !overlayLater) {
            dialogOpen = true
            AlertDialog.Builder(this)
                .setTitle("Overlay permission")
                .setMessage("Switch ko floating button dikhane ke liye overlay permission chahiye. Agli screen pe Switch ko 'Allow' karo.")
                .setPositiveButton("Open") { _, _ ->
                    dialogOpen = false
                    openOverlaySettings()
                }
                .setNegativeButton("Later") { _, _ ->
                    dialogOpen = false
                    prefs.edit().putBoolean("laterOverlay", true).apply()
                }
                .setOnCancelListener { dialogOpen = false }
                .show()
            return
        }
        if (overlayOk) prefs.edit().putBoolean("laterOverlay", false).apply()

        if (!usageOk && !usageLater) {
            dialogOpen = true
            AlertDialog.Builder(this)
                .setTitle("Usage access")
                .setMessage("Switch ko current foreground app detect karne ke liye usage access chahiye. Agli list me 'Switch' dhundho aur ON karo.")
                .setPositiveButton("Open") { _, _ ->
                    dialogOpen = false
                    openUsageSettings()
                }
                .setNegativeButton("Later") { _, _ ->
                    dialogOpen = false
                    prefs.edit().putBoolean("laterUsage", true).apply()
                }
                .setOnCancelListener { dialogOpen = false }
                .show()
            return
        }
        if (usageOk) prefs.edit().putBoolean("laterUsage", false).apply()
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openUsageSettings() {
        try {
            val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun typeName(t: Int): String = when (t) {
        0 -> "Timer"
        1 -> "Single app"
        2 -> "2-app toggle"
        3 -> "3-app cycle"
        4 -> "4-app expander"
        5 -> "5-app cycle"
        else -> "${t}-app"
    }

    private fun showAddDialog() {
        val options = arrayOf("1 Button", "2 Buttons", "3 Buttons", "4 Buttons (expander)", "5 Buttons", "Timer Button")
        AlertDialog.Builder(this)
            .setTitle("Add button")
            .setItems(options) { _, which ->
                val type = if (which == 5) 0 else which + 1
                val cfg: ButtonConfig = ButtonConfig.new(type)
                repo.updateButton(cfg)
                sendReload(); refresh()
                val target = if (type == 0) EditTimerActivity::class.java else EditButtonActivity::class.java
                val i = Intent(this, target)
                i.putExtra("id", cfg.id)
                startActivity(i)
            }.show()
    }

    private fun sendReload() {
        sendBroadcast(Intent(FloatingService.ACTION_RELOAD).setPackage(packageName))
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        else @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
