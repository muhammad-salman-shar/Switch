package com.salmanshar.appswitch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
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

class EditMiniActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var config: ButtonConfig
    private var idx: Int = 0
    private lateinit var preview: TextView

    private val swatches = intArrayOf(
        0xFFFF6D00.toInt(), 0xFFFFFFFF.toInt(), 0xFF00C853.toInt(),
        0xFF2962FF.toInt(), 0xFFD50000.toInt(), 0xFFAA00FF.toInt(),
        0xFF00BCD4.toInt(), 0xFFFFD600.toInt(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ConfigRepository(this)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        idx = intent.getIntExtra("miniIndex", 0)
        config = repo.loadButtons().firstOrNull { it.id == id } ?: run { finish(); return }
        if (idx !in config.minis.indices) { finish(); return }
        render()
    }

    private val mini get() = config.minis[idx]

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply { text = "Click #${idx + 1}"; textSize = 20f })

        preview = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24; gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(TextView(this).apply { text = "Name (max 2)"; setPadding(0, 24, 0, 0) })
        root.addView(EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(2))
            hint = "m1"
            setText(mini.name)
            addTextChangedListener(simple { s -> mini.name = s.take(2); updatePreview() })
        })

        root.addView(TextView(this).apply { text = "Time after main tap (0-5000 ms)"; setPadding(0, 24, 0, 0) })
        root.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "0"
            setText(mini.delayMs.toString())
            addTextChangedListener(simple { s ->
                mini.delayMs = (s.toLongOrNull() ?: 0L).coerceIn(0L, 5000L)
            })
        })

        root.addView(TextView(this).apply { text = "Size: ${mini.sizeDp}dp (2-100)"; setPadding(0, 24, 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 100 - 2; progress = (mini.sizeDp - 2).coerceIn(0, 98)
            setOnSeekBarChangeListener(simpleSeek { p -> mini.sizeDp = p + 2; updatePreview() })
        })

        root.addView(TextView(this).apply { text = "Transparency: ${mini.alpha}%"; setPadding(0, 24, 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 100; progress = mini.alpha
            setOnSeekBarChangeListener(simpleSeek { p -> mini.alpha = p; updatePreview() })
        })

        root.addView(TextView(this).apply { text = "Ring color"; setPadding(0, 24, 0, 12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swatches.forEach { c ->
            row.addView(View(this).apply {
                val sz = (30 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { rightMargin = 8 }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(if (c == mini.color) 4 else 1, if (c == mini.color) Color.WHITE else Color.GRAY)
                }
                setOnClickListener { mini.color = c; render() }
            })
        }
        root.addView(row)

        root.addView(TextView(this).apply {
            text = "Tap offset X: ${mini.offsetX}dp"; setPadding(0, 24, 0, 0)
        })
        root.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText(mini.offsetX.toString())
            addTextChangedListener(simple { s -> mini.offsetX = s.toIntOrNull() ?: 0 })
        })
        root.addView(TextView(this).apply {
            text = "Tap offset Y: ${mini.offsetY}dp"; setPadding(0, 12, 0, 0)
        })
        root.addView(EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText(mini.offsetY.toString())
            addTextChangedListener(simple { s -> mini.offsetY = s.toIntOrNull() ?: 0 })
        })

        root.addView(TextView(this).apply {
            text = "Position: (${mini.posX}, ${mini.posY})   Locked: ${mini.locked}"
            setPadding(0, 24, 0, 0)
        })
        root.addView(Button(this).apply {
            text = if (mini.locked) "Unlock position" else "Lock position"
            setOnClickListener { mini.locked = !mini.locked; render() }
        })
        root.addView(Button(this).apply {
            text = "Reset position to main + offset"
            setOnClickListener { mini.posX = -1; mini.posY = -1; render() }
        })

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
        val dp = mini.sizeDp * resources.displayMetrics.density
        preview.layoutParams = preview.layoutParams.apply { width = dp.toInt(); height = dp.toInt() }
        preview.text = mini.name
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, (dp * 0.4f).coerceAtLeast(1f))
        preview.alpha = mini.alpha / 100f
        val border = (dp * 0.08f).coerceAtLeast(2f)
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(border.toInt(), mini.color)
        }
        preview.requestLayout()
    }

    private fun simple(f: (String) -> Unit) = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { f(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }

    private fun simpleSeek(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { f(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
