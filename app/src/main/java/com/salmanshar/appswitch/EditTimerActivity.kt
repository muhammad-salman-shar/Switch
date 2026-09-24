package com.salmanshar.appswitch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository
import com.salmanshar.appswitch.model.MiniTimer

class EditTimerActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var config: ButtonConfig
    private lateinit var preview: TextView
    private lateinit var mainName: EditText

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

        preview = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xFF00E676.toInt())
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24; gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(TextView(this).apply { text = "Main size: ${config.sizeDp}dp"; setPadding(0, 24, 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 200 - 40; progress = config.sizeDp - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    config.sizeDp = p + 40
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        mainName = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(2))
            hint = "Main name (max 2)"
            val nm = config.subButtons.getOrNull(0)?.name ?: ""
            setText(nm)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val v = s?.toString()?.take(2) ?: ""
                    if (config.subButtons.isEmpty()) config.subButtons.add(
                        com.salmanshar.appswitch.model.SubButton("", 0xFFFFFFFF.toInt(), 45, v)
                    ) else config.subButtons[0].name = v
                    updatePreview()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(mainName)

        root.addView(TextView(this).apply {
            text = "Mini timers: ${config.minis.size}/6"; textSize = 15f; setPadding(0, 24, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "+ Add mini timer"
            isEnabled = config.minis.size < 6
            setOnClickListener {
                val nextDelay = (config.minis.maxOfOrNull { it.delayMs } ?: 0L) + 500L
                config.minis.add(MiniTimer(delayMs = nextDelay))
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
            val nmRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            nmRow.addView(TextView(this).apply { text = "Name: "; setTextColor(Color.WHITE) })
            nmRow.addView(EditText(this).apply {
                filters = arrayOf(InputFilter.LengthFilter(2))
                setText(mini.name)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) { mini.name = s?.toString()?.take(2) ?: "" }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            })
            card.addView(nmRow)

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
                    text = "Absolute delay: ${mini.delayMs}ms  (0-5000)"
                    setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
                })
                card.addView(SeekBar(this).apply {
                    max = 5000 / 10; progress = (mini.delayMs / 10).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                            mini.delayMs = p.toLong() * 10
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                })
            } else {
                card.addView(TextView(this).apply {
                    text = "Start: ${mini.delayMs}ms   Taps: ${mini.tapsCount}"
                    setTextColor(Color.WHITE); setPadding(0, 12, 0, 0)
                })
                card.addView(SeekBar(this).apply {
                    max = 5000 / 10; progress = (mini.delayMs / 10).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { mini.delayMs = p.toLong() * 10 }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
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
        updatePreview()
    }

    private fun updatePreview() {
        if (!::preview.isInitialized) return
        val dp = config.sizeDp * resources.displayMetrics.density
        preview.layoutParams = preview.layoutParams.apply { width = dp.toInt(); height = dp.toInt() }
        val nm = config.subButtons.getOrNull(0)?.name ?: ""
        preview.text = nm
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp * 0.4f)
        preview.requestLayout()
    }
}
