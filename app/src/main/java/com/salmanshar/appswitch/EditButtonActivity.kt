package com.salmanshar.appswitch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository

class EditButtonActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var config: ButtonConfig

    private val swatches = intArrayOf(
        0xFF00C853.toInt(), 0xFF2962FF.toInt(), 0xFFD50000.toInt(),
        0xFFFF6D00.toInt(), 0xFFAA00FF.toInt(), 0xFF00BCD4.toInt(),
        0xFFFFD600.toInt(), 0xFF8D6E63.toInt(), 0xFFEC407A.toInt(),
        0xFF212121.toInt(), 0xFF757575.toInt(), 0xFFFFFFFF.toInt(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ConfigRepository(this)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        config = repo.loadButtons().firstOrNull { it.id == id } ?: run { finish(); return }
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Edit  (${config.type}-button)"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Main size: ${config.sizeDp}dp"
            setPadding(0, 24, 0, 0)
        })
        root.addView(SeekBar(this).apply {
            max = 80 - 24; progress = config.sizeDp - 24
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { config.sizeDp = p + 24 }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        config.subButtons.forEachIndexed { i, sub ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(0xFF222222.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 32 }
            }
            card.addView(TextView(this).apply {
                text = "App ${i + 1}:  ${if (sub.pkg.isEmpty()) "(not selected)" else sub.pkg}"
                textSize = 13f; setTextColor(Color.WHITE)
            })
            card.addView(Button(this).apply {
                text = "Pick app"
                setOnClickListener { pickApp { pkg -> sub.pkg = pkg; render() } }
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 0)
            }
            swatches.forEach { c ->
                row.addView(View(this).apply {
                    val sz = (28 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(sz, sz).apply { rightMargin = 8 }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        setStroke(if (c == sub.color) 4 else 1, if (c == sub.color) Color.WHITE else Color.GRAY)
                    }
                    setOnClickListener { sub.color = c; render() }
                })
            }
            card.addView(row)
            card.addView(TextView(this).apply {
                text = "Sub size: ${sub.sizeDp}dp"
                setPadding(0, 12, 0, 0); setTextColor(Color.WHITE)
            })
            card.addView(SeekBar(this).apply {
                max = 80 - 24; progress = sub.sizeDp - 24
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { sub.sizeDp = p + 24 }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            root.addView(card)
        }
        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                repo.updateButton(config)
                sendBroadcast(Intent(FloatingService.ACTION_RELOAD).setPackage(packageName))
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

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
}
