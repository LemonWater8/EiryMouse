package com.example.diazymouse.ui.component

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.socket.MouseConnection
import com.example.diazymouse.ui.UiDimensions

/** DiazyMouse v1.0 Finish screen. Back is always above End. */
class PcSessionControlArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val mouseConnection: MouseConnection,
    private val onShowFinishPreview: () -> Unit,
    private val onRestoreDialogue: () -> Unit,
    private val onBackToMain: () -> Unit,
    private val onFinish: (Boolean) -> Unit
) {
    private lateinit var overlay: FrameLayout
    private lateinit var content: FrameLayout

    fun addTo(root: FrameLayout) {
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(28, 28, 28))
            visibility = View.GONE
            isClickable = true
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dimensions.fourFifthsHeightPx()
            ).apply { gravity = Gravity.BOTTOM }
        )
        content = FrameLayout(activity)
        overlay.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    fun show() {
        if (!::overlay.isInitialized) return
        showFinishPage()
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
    }

    private fun showFinishPage() {
        content.removeAllViews()
        onShowFinishPreview()

        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
        }

        // Dedicated confirmation screen requested for Config -> FINISH.
        // BACK is kept at the top edge so the user can immediately return
        // to Config Top without triggering the finish confirmation actions.
        page.addView(Button(activity).apply {
            text = "BACK"
            gravity = Gravity.CENTER
            setOnClickListener {
                onRestoreDialogue()
                hide()
                onBackToMain()
            }
        }, fullWidthWrap().apply {
            bottomMargin = 12
        })

        page.addView(TextView(activity).apply {
            text = "END?"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(12, 18, 12, 28)
        }, fullWidthWrap())

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        actions.addView(Button(activity).apply {
            text = "YES"
            setOnClickListener {
                onFinish(false)
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 12
        })

        actions.addView(Button(activity).apply {
            text = "CANCEL"
            setOnClickListener {
                onRestoreDialogue()
                hide()
                onBackToMain()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12
        })

        page.addView(actions, fullWidthWrap())

        content.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(page, FrameLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun hide() {
        if (::overlay.isInitialized) overlay.visibility = View.GONE
    }

    private fun fullWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
