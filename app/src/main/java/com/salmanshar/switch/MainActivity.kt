package com.salmanshar.switch

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var dsEdit: EditText
    private lateinit var txEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply { text = "Switch Setup"; textSize = 20f }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 24, 0, 24) }
        val prefs = getSharedPreferences("switch", MODE_PRIVATE)
        dsEdit = EditText(this).apply {
            hint = "DeepSeek package"
            setText(prefs.getString("ds", "com.deepseek.chat"))
        }
        txEdit = EditText(this).apply {
            hint = "Termux package"
            setText(prefs.getString("tx", "com.termux"))
        }
        val save = Button(this).apply {
            text = "Save packages"
            setOnClickListener {
                prefs.edit().putString("ds", dsEdit.text.toString().trim())
                    .putString("tx", txEdit.text.toString().trim()).apply()
                refresh()
            }
        }
        val b1 = Button(this).apply {
            text = "1. Grant Overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val b2 = Button(this).apply {
            text = "2. Grant Usage Access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        val b3 = Button(this).apply {
            text = "3. Start Floating Button"
            setOnClickListener {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, FloatingService::class.java))
            }
        }
        val b4 = Button(this).apply {
            text = "Stop Floating Button"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingService::class.java)) }
        }
        ll.addView(title); ll.addView(status)
        ll.addView(TextView(this).apply { text = "Package names (edit if wrong):" })
        ll.addView(dsEdit); ll.addView(txEdit); ll.addView(save)
        ll.addView(b1); ll.addView(b2); ll.addView(b3); ll.addView(b4)
        setContentView(ll)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        status.text = "Overlay: ${if (ov) "OK" else "MISSING"}\nUsage access: ${if (hasUsageAccess()) "OK" else "MISSING"}"
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
