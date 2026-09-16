package com.example.diazymouse.settings.page

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.component.SettingsButtonFactory

class SettingsValueEditorPage(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val titleText: String,
    private val initialValue: Int,
    private val minValue: Int,
    private val maxValue: Int,
    private val step: Int = 1,
    private val formatValue: (Int) -> String,
    private val onSave: (Int) -> Unit,
    private val onFinished: () -> Unit
) {
    fun show() {
        content.removeAllViews()

        var temporaryValue = initialValue.coerceIn(minValue, maxValue)

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 18, 24, 18)
        }

        val title = TextView(activity).apply {
            text = titleText
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 12)
        }

        val value = TextView(activity).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(12, 20, 12, 20)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(55, 128, 158), Color.rgb(37, 88, 118))
            ).apply {
                cornerRadius = 18f
                setStroke(2, Color.rgb(132, 208, 226))
            }
            elevation = 4f
        }

        fun refreshValue() {
            value.text = formatValue(temporaryValue)
        }

        refreshValue()

        val adjustRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }

        val minusButton = SettingsButtonFactory.compact(activity, "−") {
            temporaryValue = (temporaryValue - step).coerceAtLeast(minValue)
            refreshValue()
        }.apply { textSize = 28f }

        val plusButton = SettingsButtonFactory.compact(activity, "+") {
            temporaryValue = (temporaryValue + step).coerceAtMost(maxValue)
            refreshValue()
        }.apply { textSize = 28f }

        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val setButton = SettingsButtonFactory.compact(activity, "Set") {
            onSave(temporaryValue)
            onFinished()
        }

        val cancelButton = SettingsButtonFactory.compact(activity, "Cancel") { onFinished() }

        page.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        page.addView(value, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        adjustRow.addView(
            minusButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 12 }
        )
        adjustRow.addView(
            plusButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12 }
        )
        page.addView(adjustRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        actionRow.addView(setButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = 3 })
        actionRow.addView(cancelButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = 3 })
        page.addView(actionRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        content.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
