package com.example.diazymouse.settings.page

import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SettingsChoiceEditorPage<T>(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val titleText: String,
    private val choices: List<T>,
    private val current: T,
    private val labelFor: (T) -> String,
    private val onSave: (T) -> Unit,
    private val onFinished: () -> Unit
) {
    fun show() {
        content.removeAllViews()

        var temporary = current

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
        }

        val value = TextView(activity).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 125, 165))
            setPadding(12, 18, 12, 18)
        }

        fun refresh() {
            value.text = "$titleText : ${labelFor(temporary)}"
        }

        refresh()

        val selectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        choices.forEach { option ->
            val button = Button(activity).apply {
                text = labelFor(option)
                setOnClickListener {
                    temporary = option
                    refresh()
                }
            }
            selectRow.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val decision = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val set = Button(activity).apply {
            text = "SET"
            setOnClickListener {
                onSave(temporary)
                onFinished()
            }
        }

        val cancel = Button(activity).apply {
            text = "Cancel"
            setOnClickListener { onFinished() }
        }

        decision.addView(set, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        decision.addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        page.addView(value)
        page.addView(selectRow)
        page.addView(FrameLayout(activity), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        page.addView(decision)

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
