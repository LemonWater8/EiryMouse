package com.example.diazymouse.ui.screen

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.UiDimensions

class ConfigButtonController(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions
) {
    private var configButton: Button? = null

    fun addTo(
        root: FrameLayout,
        placeBelowAllTouchDialogue: Boolean = false,
        onOpenConfig: () -> Unit
    ) {
        val metrics = activity.resources.displayMetrics
        val buttonWidth = UiDimensions.dpToPx(86f, metrics)
        val buttonHeight = UiDimensions.dpToPx(34f, metrics)

        val button = Button(activity).apply {
            text = "Config"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(72, 72, 72))
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            visibility = View.VISIBLE
            setOnClickListener { onOpenConfig() }
        }
        configButton = button

        root.addView(
            button,
            FrameLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                if (placeBelowAllTouchDialogue) {
                    // EiryMouse action row: Info -> Send | Han/Zen | Config -> TextArea.
                    gravity = Gravity.TOP or Gravity.END
                    marginEnd = UiDimensions.dpToPx(16f, metrics)
                    topMargin = UiDimensions.dpToPx(38f, metrics)
                } else {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = dimensions.textArea1TopPx + dimensions.textArea1HeightPx - buttonHeight
                }
            }
        )
        button.bringToFront()
    }

    fun hide() {
        configButton?.visibility = View.GONE
    }

    fun show() {
        configButton?.visibility = View.VISIBLE
        configButton?.bringToFront()
    }
}
