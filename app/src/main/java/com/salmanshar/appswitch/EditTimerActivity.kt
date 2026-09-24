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
import com.salmanshar.appswitch.model.MiniTimer

class EditTimerActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var config: ButtonConfig

    private val swatches = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF00C853.toInt(), 0xFF2962FF.toInt(),
        0xFFD50000.toInt(), 0xFFFF6D00.toInt(), 0xFFAA00FF.toInt(),
        0xFF00BCD4.toInt(), 0xFFFFD600.toInt(),
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
        root.addView(TextView(this).apply { text = "Timer Settings"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Main size: ${config.sizeDp}dp"
            setPadding(0, 24, 0, 0)
        })
        root.addView(SeekBar(this).apply {
            max = 160 - 60; progress = config.sizeDp - 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { config.sizeDp = p + 60 }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply {
            text = "Mini timers: ${config.minis.size}/6"
            textSize = 15f; setPadding(0, 32, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "+ Add mini timer"
            isEnabled = config.minis.size < 6
            setOnClickListener {
                config.minis.add(MiniTimer())
                render()
            }
        })

        config.minis.forEachIndexed { idx, mini ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(0xFF222222.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
            }
            card.addView(TextView(this).apply {
                text = "Mini #${idx + 1}"; textSize = 15f; setTextColor(Color.WHITE)
            })
            card.addView(Button(this).apply {
                text = "Delete mini"
                setOnClickListener { config.minis.removeAt(idx); render() }
            })
            card.addView(TextView(this).apply {
                text = "Role: ${mini.role}"; setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
            })
            card.addView(Button(this).apply {
                text = "Toggle role (single/multi)"
                setOnClickListener {
                    mini.role = if (mini.role == "single") "multi" else "single"
                    render()
                }
            })
            if (mini.role == "single") {
                card.addView(TextView(this).apply {
                    text = "Delay: ${mini.delayMs}ms  (10-5000)"
                    setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
                })
                card.addView(SeekBar(this).apply {
                    max = (5000 - 10) / 10; progress = ((mini.delayMs - 10) / 10).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                            mini.delayMs = (p * 10 + 10).toLong()
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                })
            } else {
                card.addView(TextView(this).apply {
                    text = "Taps: ${mini.tapsCount}"; setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
                })
                card.addView(SeekBar(this).apply {
                    max = 100 - 1; progress = mini.tapsCount - 1
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { mini.tapsCount = p + 1 }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                })
                card.addView(TextView(this).apply {
                    text = "Window: ${mini.windowMs}ms"; setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
                })
                card.addView(SeekBar(this).apply {
                    max = (5000 - 100) / 100; progress = ((mini.windowMs - 100) / 100).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                            mini.windowMs = (p * 100 + 100).toLong()
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                })
            }
            card.addView(TextView(this).apply {
                text = "Size: ${mini.sizeDp}dp"; setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
            })
            card.addView(SeekBar(this).apply {
                max = 60 - 12; progress = mini.sizeDp - 12
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { mini.sizeDp = p + 12 }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            card.addView(TextView(this).apply {
                text = "Transparency: ${mini.alpha}%"; setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
            })
            card.addView(SeekBar(this).apply {
                max = 100; progress = mini.alpha
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { mini.alpha = p }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
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
                        setStroke(if (c == mini.color) 4 else 1, if (c == mini.color) Color.WHITE else Color.GRAY)
                    }
                    setOnClickListener { mini.color = c; render() }
                })
            }
            card.addView(row)
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
}
