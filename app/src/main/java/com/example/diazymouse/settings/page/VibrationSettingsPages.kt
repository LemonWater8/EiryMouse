package com.example.diazymouse.settings.page

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.settings.store.VibrationSettingsStore
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiVibrator
import com.example.diazymouse.ui.component.SettingsButtonFactory

/**
 * Vibration value configuration pages.
 */
class VibrationSettingsPages(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val onBackToMain: () -> Unit
) {

    private val uiVibrator =
        UiVibrator(
            activity
        )

    private enum class EditTarget {
        DURATION,
        AMPLITUDE
    }

    fun show() {

        content.removeAllViews()

        val page =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20,
                    20,
                    20,
                    20
                )
            }

        val header =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(activity).apply {

                text =
                    "Vibration Values"

                textSize =
                    20f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val backButton =
            SettingsButtonFactory.compact(activity, "Back") { onBackToMain() }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            backButton
        )

        page.addView(
            header
        )

        val scrollView =
            ScrollView(activity)

        val list =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        UiVibrationConfig.ALL_PATTERNS.forEach {
            item ->

            val current =
                VibrationSettingsStore.getPattern(
                    activity,
                    item.pattern
                )

            val row =
                TextView(activity).apply {

                    text =
                        "${item.name}    " +
                            "${current.durationMs} ms    " +
                            "Amp ${current.amplitude}"

                    textSize =
                        16f

                    setTextColor(
                        Color.WHITE
                    )

                    setPadding(
                        12,
                        14,
                        12,
                        14
                    )

                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.rgb(69, 78, 90), Color.rgb(43, 49, 58))
                    ).apply {
                        cornerRadius = 16f
                        setStroke(2, Color.rgb(106, 124, 145))
                    }
                    elevation = 3f

                    isClickable =
                        true

                    setOnClickListener {

                        showVibrationEditPage(
                            item
                        )
                    }
                }

            val rowParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    bottomMargin =
                        5
                }

            list.addView(
                row,
                rowParams
            )
        }

        scrollView.addView(
            list,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        content.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun showVibrationEditPage(
        item: UiVibrationConfig.NamedPattern
    ) {

        content.removeAllViews()

        val saved =
            VibrationSettingsStore.getPattern(
                activity,
                item.pattern
            )

        var temporaryDuration =
            saved.durationMs

        var temporaryAmplitude =
            saved.amplitude

        var selectedTarget =
            EditTarget.DURATION

        val page =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    24,
                    18,
                    24,
                    18
                )
            }

        val title =
            TextView(activity).apply {

                text =
                    item.name

                textSize =
                    20f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    12,
                    8,
                    12,
                    12
                )
            }

        page.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val valuesRow =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val durationValue =
            TextView(activity)

        val amplitudeValue =
            TextView(activity)

        fun valueBackground(
            selected: Boolean
        ): GradientDrawable =
            GradientDrawable().apply {

                setColor(
                    if (
                        selected
                    ) {
                        Color.rgb(
                            230,
                            110,
                            45
                        )
                    } else {
                        Color.rgb(
                            25,
                            125,
                            165
                        )
                    }
                )

                setStroke(
                    2,
                    if (selected) Color.rgb(255, 188, 120) else Color.rgb(132, 208, 226)
                )
                cornerRadius = 18f
            }

        fun refreshValues() {

            durationValue.text =
                "${temporaryDuration} ms"

            amplitudeValue.text =
                "Amp ${temporaryAmplitude}"

            durationValue.background =
                valueBackground(
                    selectedTarget ==
                        EditTarget.DURATION
                )

            amplitudeValue.background =
                valueBackground(
                    selectedTarget ==
                        EditTarget.AMPLITUDE
                )
        }

        durationValue.apply {

            textSize =
                17f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            setPadding(
                12,
                18,
                12,
                18
            )

            isClickable =
                true

            setOnClickListener {

                selectedTarget =
                    EditTarget.DURATION

                refreshValues()
            }
        }

        amplitudeValue.apply {

            textSize =
                17f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            setPadding(
                12,
                18,
                12,
                18
            )

            isClickable =
                true

            setOnClickListener {

                selectedTarget =
                    EditTarget.AMPLITUDE

                refreshValues()
            }
        }

        valuesRow.addView(
            durationValue,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    12
            }
        )

        valuesRow.addView(
            amplitudeValue,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    12
            }
        )

        page.addView(
            valuesRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val adjustRow =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    18,
                    0,
                    18
                )
            }

        val minusButton =
            SettingsButtonFactory.compact(activity, "−") {
                when (selectedTarget) {
                    EditTarget.DURATION -> {
                        temporaryDuration = (temporaryDuration - 1L).coerceAtLeast(
                            VibrationSettingsStore.MIN_DURATION_MS
                        )
                    }
                    EditTarget.AMPLITUDE -> {
                        temporaryAmplitude = (temporaryAmplitude - 1).coerceAtLeast(
                            VibrationSettingsStore.MIN_AMPLITUDE
                        )
                    }
                }
                refreshValues()
            }.apply { textSize = 28f }

        val plusButton =
            SettingsButtonFactory.compact(activity, "+") {
                when (selectedTarget) {
                    EditTarget.DURATION -> {
                        temporaryDuration = (temporaryDuration + 1L).coerceAtMost(
                            VibrationSettingsStore.MAX_DURATION_MS
                        )
                    }
                    EditTarget.AMPLITUDE -> {
                        temporaryAmplitude = (temporaryAmplitude + 1).coerceAtMost(
                            VibrationSettingsStore.MAX_AMPLITUDE
                        )
                    }
                }
                refreshValues()
            }.apply { textSize = 28f }

        adjustRow.addView(
            minusButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd =
                    16
            }
        )

        adjustRow.addView(
            plusButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart =
                    16
            }
        )

        page.addView(
            adjustRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val previewButton =
            SettingsButtonFactory.fullWidth(activity, "TEST") {
                uiVibrator.vibrateDirect(
                    durationMs = temporaryDuration,
                    amplitude = temporaryAmplitude
                )
            }

        page.addView(
            previewButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 5 }
        )

        val decisionRow =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        val setButton =
            SettingsButtonFactory.compact(activity, "SET") {
                VibrationSettingsStore.savePattern(
                    context = activity,
                    defaultPattern = item.pattern,
                    durationMs = temporaryDuration,
                    amplitude = temporaryAmplitude
                )
                show()
            }

        val cancelButton =
            SettingsButtonFactory.compact(activity, "Cancel") {
                // Nothing is saved; return to the list with the previous value.
                show()
            }

        decisionRow.addView(
            setButton,
            LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 3 }
        )

        decisionRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = 3 }
        )

        page.addView(
            decisionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        refreshValues()

        content.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

}
