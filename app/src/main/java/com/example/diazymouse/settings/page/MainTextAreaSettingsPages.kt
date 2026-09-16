package com.example.diazymouse.settings.page

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.settings.store.FontSettingsStore
import com.example.diazymouse.settings.store.MainTextAreaColorSettingsStore
import com.example.diazymouse.settings.store.MainTextAreaFontSizeStore

/**
 * Main text area configuration page.
 *
 * Owns:
 * - font selection
 * - font-size editor
 * - background/text color editors
 */
class MainTextAreaSettingsPages(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val onBackToMain: () -> Unit,
    private val onMainTextAreaStyleChanged: () -> Unit
) {

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
                    "Main Text Area / Config"

                textSize =
                    20f

                setTextColor(
                    Color.WHITE
                )
            }

        val backButton =
            Button(activity).apply {

                text =
                    "Back"

                setOnClickListener {
                    onBackToMain()
                }
            }

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

        val fontButton =
            Button(activity)

        fun refreshFontText() {

            fontButton.text =
                "Font : " +
                    FontSettingsStore.getMode(
                        activity
                    ).displayName
        }

        refreshFontText()

        fontButton.setOnClickListener {

            FontSettingsStore.cycle(
                activity
            )

            refreshFontText()

            onMainTextAreaStyleChanged()
        }

        page.addView(
            fontButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val fontSizeButton =
            Button(activity).apply {

                text =
                    "Font Size : " +
                        MainTextAreaFontSizeStore.getSizeSp(
                            activity
                        ) +
                        " sp"

                setOnClickListener {
                    SettingsValueEditorPage(
                        activity = activity,
                        content = content,
                        titleText = "Font Size",
                        initialValue = MainTextAreaFontSizeStore.getSizeSp(activity),
                        minValue = MainTextAreaFontSizeStore.MIN_SIZE_SP,
                        maxValue = MainTextAreaFontSizeStore.MAX_SIZE_SP,
                        formatValue = { value -> "$value sp" },
                        onSave = { size ->
                            MainTextAreaFontSizeStore.saveSizeSp(activity, size)
                            onMainTextAreaStyleChanged()
                            show()
                        },
                        onFinished = { show() }
                    ).show()
                }
            }

        page.addView(
            fontSizeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val backgroundButton =
            Button(activity).apply {

                text =
                    "Background Color"

                setOnClickListener {
                    val initial = MainTextAreaColorSettingsStore.getBackground(activity)
                    SettingsColorEditorPage(
                        activity = activity,
                        content = content,
                        titleText = "Main Text Area Background",
                        initial = SettingsColorEditorPage.RgbChannels(
                            red = initial.red,
                            blue = initial.blue,
                            green = initial.green
                        ),
                        onSave = { value ->
                            MainTextAreaColorSettingsStore.saveBackground(
                                activity,
                                MainTextAreaColorSettingsStore.RbgChannels(
                                    red = value.red,
                                    blue = value.blue,
                                    green = value.green
                                )
                            )
                            onMainTextAreaStyleChanged()
                        },
                        onFinished = { show() }
                    ).show()
                }
            }

        page.addView(
            backgroundButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val textColorButton =
            Button(activity).apply {

                text =
                    "Text Color"

                setOnClickListener {
                    val initial = MainTextAreaColorSettingsStore.getText(activity)
                    SettingsColorEditorPage(
                        activity = activity,
                        content = content,
                        titleText = "Main Text Area Text",
                        initial = SettingsColorEditorPage.RgbChannels(
                            red = initial.red,
                            blue = initial.blue,
                            green = initial.green
                        ),
                        onSave = { value ->
                            MainTextAreaColorSettingsStore.saveText(
                                activity,
                                MainTextAreaColorSettingsStore.RbgChannels(
                                    red = value.red,
                                    blue = value.blue,
                                    green = value.green
                                )
                            )
                            onMainTextAreaStyleChanged()
                        },
                        onFinished = { show() }
                    ).show()
                }
            }

        page.addView(
            textColorButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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
