package com.example.diazymouse.ui.component

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.example.diazymouse.settings.page.MouseSettingsPages
import com.example.diazymouse.settings.page.VibrationSettingsPages
import com.example.diazymouse.ui.UiDimensions

/** Config navigation for the settings pages still used by EiryMouse. */
class SettingsPageController(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val content: FrameLayout,
    private val onClose: () -> Unit,
    private val onBackToMain: () -> Unit
) {
    private val vibrationPages = VibrationSettingsPages(
        activity = activity,
        content = content,
        onBackToMain = onBackToMain
    )

    private val mousePages = MouseSettingsPages(
        activity = activity,
        content = content,
        onBackToMain = onBackToMain
    )

    private val mainPageBuilder = SettingsMainPageBuilder(
        activity = activity,
        dimensions = dimensions
    )

    private fun animateCurrentPage() {
        val view = content.getChildAt(0) ?: return
        val density = activity.resources.displayMetrics.density
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 12f * density
        view.scaleX = 0.988f
        view.scaleY = 0.988f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(205L)
            .start()
    }

    fun showVibrationPage() {
        vibrationPages.show()
        animateCurrentPage()
    }

    fun showMousePage() {
        mousePages.show()
        animateCurrentPage()
    }

    fun showMainPage() {
        content.removeAllViews()
        val page = mainPageBuilder.build(
            onClose = onClose,
            onOpenVibration = { vibrationPages.show() },
            onOpenMouse = { mousePages.show() }
        )
        content.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        animateCurrentPage()
    }
}
