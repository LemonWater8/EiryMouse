package com.example.diazymouse.ui.screen

import android.graphics.Color
import android.view.Gravity
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.component.SettingsPageController
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/**
 * Config overlay lifecycle and placement only.
 *
 * Page construction has been moved to SettingsPageController.
 */
class SettingsScreenArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val onBackToMain: () -> Unit = {}
) {

    private lateinit var overlay:
        FrameLayout

    private lateinit var content:
        FrameLayout

    private lateinit var pages:
        SettingsPageController

    fun addTo(
        root: FrameLayout
    ) {

        overlay =
            FrameLayout(activity).apply {

                setBackgroundColor(
                    Color.rgb(
                        28,
                        28,
                        28
                    )
                )

                visibility =
                    View.GONE

                isClickable =
                    true
            }

        // Cover the full height of the current screen so the overlay completely
        // covers the keyboard area immediately beneath the Han/Zen row.
        val topMarginPx = 0
        val overlayCoverRatio = 1.0f
        val overlayHeightPx = (dimensions.screenHeightPx * overlayCoverRatio).toInt()
        val params =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                overlayHeightPx
            ).apply {

                gravity =
                    Gravity.TOP

                topMargin =
                    topMarginPx
            }

        root.addView(
            overlay,
            params
        )

        content =
            FrameLayout(
                activity
            )

        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        pages =
            SettingsPageController(
                activity =
                    activity,
                dimensions =
                    dimensions,
                content =
                    content,
                onClose = {
                    hide()
                    onBackToMain()
                },
                onBackToMain = {
                    hide()
                    onBackToMain()
                }
            )

        pages.showMainPage()
    }

    fun showVibration() {
        if (!::overlay.isInitialized || !::pages.isInitialized) return
        pages.showVibrationPage()
        showOverlayAnimated()
    }

    fun showMouse() {
        if (!::overlay.isInitialized || !::pages.isInitialized) return
        pages.showMousePage()
        showOverlayAnimated()
    }

    fun show() {

        if (
            !::overlay.isInitialized ||
            !::pages.isInitialized
        ) {
            return
        }

        pages.showMainPage()

        showOverlayAnimated()
    }

    private fun showOverlayAnimated() {
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.scaleX = 0.992f
        overlay.scaleY = 0.992f
        overlay.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .start()
    }

    private fun hide() {

        if (
            ::overlay.isInitialized
        ) {

            overlay.visibility =
                View.GONE
        }
    }

    /**
     * Config starts immediately below Text Area 2.
     */
    private fun calculateContentTop():
        Int =
        dimensions.textArea2TopPx +
            dimensions.textArea2HeightPx

}
