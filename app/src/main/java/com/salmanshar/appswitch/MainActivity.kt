package com.salmanshar.appswitch

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var btnA: Button
    private lateinit var btnB: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("switch", MODE_PRIVATE)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        ll.addView(TextView(this).apply { text = "Switch Setup"; textSize = 20f })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 24, 0, 24) }
        ll.addView(status)

        btnA = Button(this).apply {
            setOnClickListener { pickApp { pkg -> prefs.edit().putString("pkgA", pkg).apply(); refresh() } }
        }
        btnB = Button(this).apply {
            setOnClickListener { pickApp { pkg -> prefs.edit().putString("pkgB", pkg).apply(); refresh() } }
        }
        ll.addView(btnA); ll.addView(btnB)

        ll.addView(Button(this).apply {
            text = "1. Grant Overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        ll.addView(Button(this).apply {
            text = "2. Grant Usage Access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        })
        ll.addView(Button(this).apply {
            text = "3. Start Floating Button"
            setOnClickListener {
                if (prefs.getString("pkgA", "").isNullOrEmpty() || prefs.getString("pkgB", "").isNullOrEmpty()) {
                    status.text = "Pehle App A aur App B dono pick karo."
                    return@setOnClickListener
                }
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, FloatingService::class.java))
            }
        })
        ll.addView(Button(this).apply {
            text = "Stop Floating Button"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingService::class.java)) }
        })
        setContentView(ll)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val a = prefs.getString("pkgA", "")
        val b = prefs.getString("pkgB", "")
        btnA.text = "App A: " + (if (a.isNullOrEmpty()) "(tap to pick)" else label(a))
        btnB.text = "App B: " + (if (b.isNullOrEmpty()) "(tap to pick)" else label(b))
        val ov = Settings.canDrawOverlays(this)
        status.text = "Overlay: ${if (ov) "OK" else "MISSING"}   Usage access: ${if (hasUsageAccess()) "OK" else "MISSING"}"
    }

    private fun label(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) { pkg }

    private fun pickApp(onPicked: (String) -> Unit) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = apps.map { "${pm.getApplicationLabel(it)}  (${it.packageName})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pick app")
            .setItems(labels) { _, i -> onPicked(apps[i].packageName) }
            .show()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        else
            @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
