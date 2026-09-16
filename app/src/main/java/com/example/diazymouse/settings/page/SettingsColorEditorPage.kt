package com.example.diazymouse.settings.page

import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity

class SettingsColorEditorPage(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val titleText: String,
    private val initial: RgbChannels,
    private val onSave: (RgbChannels) -> Unit,
    private val onFinished: () -> Unit
) {
    data class RgbChannels(
        val red: Int,
        val blue: Int,
        val green: Int
    ) {
        fun toColor(): Int = Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
    }

    fun show() {
        content.removeAllViews()

        var red = initial.red
        var blue = initial.blue
        var green = initial.green

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }

        val title = TextView(activity).apply {
            text = titleText
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 14)
        }

        val preview = TextView(activity).apply {
            text = "Preview"
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(8, 12, 8, 12)
        }

        fun updatePreview() {
            preview.setBackgroundColor(Color.rgb(red, green, blue))
            val brightness = (red + green + blue) / 3
            preview.setTextColor(if (brightness < 128) Color.WHITE else Color.BLACK)
        }

        fun addSlider(label: String, startValue: Int, onChanged: (Int) -> Unit) {
            val valueText = TextView(activity).apply {
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val seekBar = SeekBar(activity).apply {
                max = 255
                progress = startValue
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$label : $progress"
                        onChanged(progress)
                        updatePreview()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }

            valueText.text = "$label : $startValue"
            page.addView(valueText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            page.addView(seekBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        updatePreview()

        val spacer = FrameLayout(activity)

        val decisionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val setButton = Button(activity).apply {
            text = "SET"
            setOnClickListener {
                onSave(RgbChannels(red = red, blue = blue, green = green))
                onFinished()
            }
        }

        val cancelButton = Button(activity).apply {
            text = "Cancel"
            setOnClickListener { onFinished() }
        }

        page.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        page.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        addSlider("Red", red) { value -> red = value }
        addSlider("Blue", blue) { value -> blue = value }
        addSlider("Green", green) { value -> green = value }
        page.addView(spacer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        decisionRow.addView(setButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        decisionRow.addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(decisionRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        content.addView(
            ScrollView(activity).apply {
                isFillViewport = true
                addView(
                    page,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
