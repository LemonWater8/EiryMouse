package com.example.diazymouse.settings.page

import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.settings.store.MouseSettingsStore
import com.example.diazymouse.ui.component.SettingsButtonFactory

/**
 * Mouse configuration pages.
 *
 * Navigation:
 * Config -> Mouse / Config -> MouseSpeed or ScrollRing -> edit page
 *
 * Values are changed temporarily with - / + and are persisted only by Set.
 * Cancel discards the temporary value.
 */
class MouseSettingsPages(
    private val activity: ComponentActivity,
    private val content: FrameLayout,
    private val onBackToMain: () -> Unit
) {

    fun show() {
        content.removeAllViews()

        val page =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
            }

        val header =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(activity).apply {
                text = "Mouse / Config"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
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
        header.addView(backButton)
        page.addView(header)

        val mouseSpeedButton =
            SettingsButtonFactory.fullWidth(activity, "MouseSpeed") {
                    SettingsValueEditorPage(
                        activity = activity,
                        content = content,
                        titleText = "MouseSpeed",
                        initialValue = MouseSettingsStore.getMouseSpeedPercent(activity),
                        minValue = MouseSettingsStore.MIN_MOUSE_SPEED_PERCENT,
                        maxValue = MouseSettingsStore.MAX_MOUSE_SPEED_PERCENT,
                        step = MouseSettingsStore.MOUSE_SPEED_STEP_PERCENT,
                        formatValue = { value -> "$value %" },
                        onSave = { value ->
                            MouseSettingsStore.setMouseSpeedPercent(activity, value)
                        },
                        onFinished = { show() }
                    ).show()
            }

        page.addView(
            mouseSpeedButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 5 }
        )

        val scrollRingButton =
            SettingsButtonFactory.fullWidth(activity, "ScrollRing") {
                    SettingsValueEditorPage(
                        activity = activity,
                        content = content,
                        titleText = "ScrollRing",
                        initialValue = MouseSettingsStore.getScrollRingAmount(activity),
                        minValue = MouseSettingsStore.MIN_SCROLL_RING_AMOUNT,
                        maxValue = MouseSettingsStore.MAX_SCROLL_RING_AMOUNT,
                        step = MouseSettingsStore.SCROLL_RING_STEP,
                        formatValue = { value -> value.toString() },
                        onSave = { value ->
                            MouseSettingsStore.setScrollRingAmount(activity, value)
                        },
                        onFinished = { show() }
                    ).show()
            }

        page.addView(
            scrollRingButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 5 }
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

