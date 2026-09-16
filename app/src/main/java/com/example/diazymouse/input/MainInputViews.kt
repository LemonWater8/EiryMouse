package com.example.diazymouse.input

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions

/**
 * Creates only the visual views for the main input region.
 *
 * No mouse command or touch-controller logic lives here.
 */
class MainInputViews(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions
) {

    fun createScrollArea(
        root: FrameLayout
    ): FrameLayout {

        val scrollArea =
            FrameLayout(activity).apply {

                setBackgroundColor(
                    UiConfig.SCROLL_AREA_BACKGROUND
                )
            }

        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dimensions.scrollAreaHeightPx
            ).apply {

                gravity =
                    Gravity.TOP

                leftMargin =
                    dimensions.buttonWidthPx

                rightMargin =
                    dimensions.buttonWidthPx

                topMargin =
                    dimensions.mainAreaTopPx
            }

        root.addView(
            scrollArea,
            params
        )

        return scrollArea
    }

    fun createTouchPad(
        root: FrameLayout
    ): FrameLayout {

        val touchPad =
            FrameLayout(activity).apply {

                setBackgroundColor(
                    Color.WHITE
                )

                isClickable =
                    true
            }

        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dimensions.touchPadHeightPx
            ).apply {

                gravity =
                    Gravity.TOP

                leftMargin =
                    dimensions.buttonWidthPx

                rightMargin =
                    dimensions.buttonWidthPx

                topMargin =
                    dimensions.touchPadTopPx
            }

        root.addView(
            touchPad,
            params
        )

        addTouchPadInfo(
            touchPad
        )

        return touchPad
    }

    /**
     * The MyMoveMouse label is centered both horizontally and vertically.
     * Font size is 1.5x the previous INFO_TEXT_SIZE_SP.
     */
    private fun addTouchPadInfo(
        touchPad: FrameLayout
    ) {

        val infoText =
            TextView(activity).apply {

                text =
                    UiConfig.TOUCH_PAD_INFO_TEXT

                setTextColor(
                    Color.BLACK
                )

                textSize =
                    UiConfig.TOUCH_PAD_INFO_TEXT_SIZE_SP

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    0
                )

                isClickable =
                    false

                isFocusable =
                    false
            }

        touchPad.addView(
            infoText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
