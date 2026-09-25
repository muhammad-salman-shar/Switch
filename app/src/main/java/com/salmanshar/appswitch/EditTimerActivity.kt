package com.salmanshar.appswitch

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salmanshar.appswitch.model.ButtonConfig
import com.salmanshar.appswitch.model.ConfigRepository

class EditTimerActivity : AppCompatActivity() {
    private lateinit var repo: ConfigRepository
    private lateinit var config: ButtonConfig
    private lateinit var preview: TextView

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
        root.addView(TextView(this).apply { text = "Trigger Settings"; textSize = 20f })

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

        root.addView(TextView(this).apply { text = "Name (max 2)"; setPadding(0, 24, 0, 0) })
        root.addView(EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(2))
            hint = "MN"
            setText(config.name)
            addTextChangedListener(simple { s -> config.name = s.take(2); updatePreview() })
        })

        root.addView(TextView(this).apply { text = "Size: ${config.sizeDp}dp (2-100)"; setPadding(0, 24, 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 100 - 2; progress = (config.sizeDp - 2).coerceIn(0, 98)
            setOnSeekBarChangeListener(simpleSeek { p -> config.sizeDp = p + 2; updatePreview() })
        })

        root.addView(TextView(this).apply { text = "Transparency: ${config.alpha}%"; setPadding(0, 24, 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 100; progress = config.alpha
            setOnSeekBarChangeListener(simpleSeek { p -> config.alpha = p; updatePreview() })
        })

        root.addView(TextView(this).apply {
            text = "Clicks: ${config.minis.size}/6"; textSize = 15f; setPadding(0, 32, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "+ Add Click"
            isEnabled = config.minis.size < 6
            setOnClickListener {
                config.minis.add(com.salmanshar.appswitch.model.MiniTimer(
                    delayMs = 0L, name = "m${config.minis.size + 1}"
                ))
                render()
            }
        })

        config.minis.forEachIndexed { idx, mini ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            row.addView(TextView(this).apply {
                text = "#${idx + 1}  ${mini.name.ifEmpty { "?" }}   ${mini.delayMs}ms   ${mini.sizeDp}dp"
                textSize = 13f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    val i = Intent(this@EditTimerActivity, EditMiniActivity::class.java)
                    i.putExtra("id", config.id)
                    i.putExtra("miniIndex", idx)
                    startActivity(i)
                }
            })
            row.addView(Button(this).apply {
                text = "X"
                setOnClickListener { config.minis.removeAt(idx); render() }
            })
            root.addView(row)
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

    override fun onResume() {
        super.onResume()
        val fresh = repo.loadButtons().firstOrNull { it.id == config.id }
        if (fresh != null) { config = fresh; render() }
    }

    private fun updatePreview() {
        if (!::preview.isInitialized) return
        val dp = config.sizeDp * resources.displayMetrics.density
        preview.layoutParams = preview.layoutParams.apply { width = dp.toInt(); height = dp.toInt() }
        preview.text = config.name
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp * 0.35f)
        preview.alpha = config.alpha / 100f
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
