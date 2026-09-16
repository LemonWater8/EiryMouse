package com.example.diazymouse.ui.component

import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator

class SettingsMainPageBuilder(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions
) {
    fun build(
        onClose: () -> Unit,
        onOpenVibration: () -> Unit,
        onOpenMouse: () -> Unit
    ): View {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        lateinit var vibrationButton: Button
        vibrationButton = SettingsButtonFactory.rowButton(
            activity = activity,
            text = if (UiVibrator.isEnabled(activity)) "Vibrator : ON" else "Vibrator : OFF",
            onClick = {
                val nextState = !UiVibrator.isEnabled(activity)
                UiVibrator.setEnabled(activity, nextState)
                vibrationButton.text = if (UiVibrator.isEnabled(activity)) "Vibrator : ON" else "Vibrator : OFF"
            },
            weight = 1f
        )

        val closeButton = SettingsButtonFactory.compact(
            activity = activity,
            text = "Back",
            onClick = onClose
        )

        topRow.addView(vibrationButton)
        topRow.addView(closeButton)

        page.addView(
            topRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(
            SettingsButtonFactory.fullWidth(activity, "Vibration Values") { onOpenVibration() }
        )
        page.addView(
            SettingsButtonFactory.fullWidth(activity, "Mouse / Config") { onOpenMouse() }
        )

        return ScrollView(activity).apply {
            isFillViewport = true
            addView(
                page,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }
}
