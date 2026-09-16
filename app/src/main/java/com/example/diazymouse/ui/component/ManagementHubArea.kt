package com.example.diazymouse.ui.component

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.settings.model.OperationMode
import com.example.diazymouse.settings.store.FloatingSettingsStore
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator
import com.example.diazymouse.ui.Win98Style

/**
 * DiazyMouse v1.1 Config hub.
 *
 * Classic and AllTouch share the same safe navigation rules, but expose only
 * the settings that are relevant to their transport/UI mode.
 * Classic Config is always vertically scrollable; the Classic mouse UI itself
 * is not resized or made scrollable.
 */
class ManagementHubArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val onConnect: () -> Unit,
    private val onMarkPosition: () -> Unit,
    private val onOpenVibration: () -> Unit,
    private val onOpenMouse: () -> Unit,
    private val modeProvider: () -> OperationMode,
    private val onModeChanged: (OperationMode) -> Unit,
    private val statusProvider: () -> Pair<Boolean, String>,
    private val onDisconnect: () -> Unit,
    private val screenAwakeProvider: () -> Boolean,
    private val onScreenAwakeChanged: (Boolean) -> Unit,
    private val onFinishConfirmed: () -> Unit,
    private val orientationLabelProvider: () -> String,
    private val onToggleOrientation: () -> Unit,
    private val onFinishScreenEntered: () -> Unit = {},
    private val onFinishScreenExited: () -> Unit = {},
    private val onBackToMain: () -> Unit = {}
) {
    private lateinit var overlay: FrameLayout
    private lateinit var content: FrameLayout

    fun addTo(root: FrameLayout) {
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(Win98Style.DESKTOP)
            visibility = View.GONE
            isClickable = true
        }
        // Cover the full height of the current screen so the overlay completely
        // covers the keyboard area immediately beneath the Han/Zen row.
        val topMarginPx = 0
        val overlayCoverRatio = 1.0f
        val overlayHeightPx = (dimensions.screenHeightPx * overlayCoverRatio).toInt()
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                overlayHeightPx
            ).apply {
                gravity = Gravity.TOP
                topMargin = topMarginPx
            }
        )
        content = FrameLayout(activity)
        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun show() {
        if (!::overlay.isInitialized || !::content.isInitialized) return
        buildTopPage()
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(180L).start()
    }

    private fun hide() {
        if (::overlay.isInitialized) overlay.visibility = View.GONE
    }

    private fun showContentAnimated(view: View, riseDp: Int = 14) {
        content.removeAllViews()
        content.addView(view, matchParent())
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(riseDp).toFloat()
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(210L)
            .start()
    }

    private fun buildTopPage() {
        content.removeAllViews()
        val operationMode = modeProvider()
        val (connected, targetName) = statusProvider()

        val page = newPage()

        // Top page is intentionally compact: keep only a 1px vertical gap
        // between neighboring controls while preserving the existing order/width.
        page.addView(statusPanel(connected, targetName), topPageFullWidthWrap(first = true))

        // Back is the first actionable control so every Config screen has a guaranteed escape path.
        page.addView(button("BACK") {
            hide()
            onBackToMain()
        }, topPageFullWidthWrap())

        page.addView(button("CONNECT") {
            hide()
            onConnect()
        }, topPageFullWidthWrap())

        page.addView(button("SETTING") { buildSettingPage() }, topPageFullWidthWrap())
        page.addView(button("FINISH") {
            buildFinishPage()
        }, topPageFullWidthWrap())

        val orientationButton = button(orientationLabelProvider()) { }
        attachThreeSecondLongPress(orientationButton) {
            onToggleOrientation()
        }
        page.addView(orientationButton, topPageFullWidthWrap())

        // Always scroll Config. This specifically fixes portrait Classic Config clipping.
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                page,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        showContentAnimated(scroll)
    }


    private fun buildFinishPage() {
        onFinishScreenEntered()

        val page = FrameLayout(activity).apply {
            setPadding(dp(24), dp(18), dp(24), dp(24))
        }

        page.addView(button("BACK") {
            onFinishScreenExited()
            buildTopPage()
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        })

        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        center.addView(TextView(activity).apply {
            text = "Finish"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, fullWidthWrap().apply {
            bottomMargin = dp(24)
        })

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(button("YES") {
            onFinishConfirmed()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })
        actions.addView(button("NO") {
            onFinishScreenExited()
            buildTopPage()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        center.addView(actions, fullWidthWrap())

        page.addView(center, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })

        // Keep every Config destination scrollable, including FINISH.
        showContentAnimated(ScrollView(activity).apply {
            isFillViewport = true
            addView(page, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }, 10)
    }

    private fun attachThreeSecondLongPress(view: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var fired = false
        var runnable: Runnable? = null
        val originalBackground = view.background
        val holdBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(88, 145, 84), Color.rgb(47, 98, 52))
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(2), Color.rgb(151, 218, 146))
        }

        fun restoreVisual() {
            view.background = originalBackground
            view.scaleX = 1f
            view.scaleY = 1f
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    runnable?.let(handler::removeCallbacks)
                    // Show a distinct hold state while the orientation button is being held.
                    view.background = holdBackground
                    runnable = Runnable {
                        fired = true
                        action()
                    }.also { handler.postDelayed(it, 2_000L) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    runnable?.let(handler::removeCallbacks)
                    runnable = null
                    // If the finger leaves before completion, immediately return to the normal color.
                    restoreVisual()
                    true
                }
                else -> true
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun richPanelBackground(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(49, 55, 64), Color.rgb(34, 38, 46))
    ).apply {
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(92, 112, 132))
    }

    private fun modePanel(): View {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
            elevation = dp(2).toFloat()
        }
        panel.addView(label("MODE"), fullWidthWrap())
        val group = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val classic = radio("Classic")
        val allTouch = radio("AllTouch")
        group.addView(classic)
        group.addView(allTouch)
        when (modeProvider()) {
            OperationMode.CLASSIC -> classic.isChecked = true
            OperationMode.ALL_TOUCH -> allTouch.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val next = when (checkedId) {
                classic.id -> OperationMode.CLASSIC
                allTouch.id -> OperationMode.ALL_TOUCH
                else -> return@setOnCheckedChangeListener
            }
            if (next != modeProvider()) {
                // Requirement: changing mode always disconnects first.
                onDisconnect()
                hide()
                onModeChanged(next)
            }
        }
        panel.addView(group, fullWidthWrap())
        return panel
    }

    private fun statusPanel(connected: Boolean, targetName: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Reduce only the vertical size of the status area. Horizontal geometry stays unchanged.
            setPadding(dp(12), dp(3), dp(12), dp(3))
            background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
            elevation = dp(2).toFloat()
            addView(compactStatusLabel("CONNECTION STATUS", 11f, Win98Style.TITLE_BLUE), statusLineParams())
            addView(
                compactStatusLabel(
                    if (connected) "Connecting" else "NoConnecting",
                    14f,
                    Win98Style.TEXT
                ),
                statusLineParams()
            )
            addView(
                compactStatusLabel("Target: ${targetName.ifBlank { "Unknown" }}", 12f, Win98Style.TEXT),
                statusLineParams()
            )
        }

    private fun buildSettingPage() {
        val page = newPage()
        page.addView(button("BACK") { buildTopPage() }, fullWidthWrap())
        page.addView(label("SETTING"), fullWidthWrap())

        page.addView(button("STOWAGE TIME") { buildStowageTimePage() }, fullWidthWrap())

        page.addView(button("MARK POSITION") {
            hide()
            onMarkPosition()
        }, fullWidthWrap())

        lateinit var vibrationButton: Button
        vibrationButton = button(if (UiVibrator.isEnabled(activity)) "Vibrator : ON" else "Vibrator : OFF") {
            val next = !UiVibrator.isEnabled(activity)
            UiVibrator.setEnabled(activity, next)
            vibrationButton.text = if (next) "Vibrator : ON" else "Vibrator : OFF"
        }
        page.addView(vibrationButton, fullWidthWrap())

        page.addView(button("Vibration Values") {
            hide()
            onOpenVibration()
        }, fullWidthWrap())
        page.addView(button("Mouse / Config") {
            hide()
            onOpenMouse()
        }, fullWidthWrap())
        page.addView(screenAwakePanel(), fullWidthWrap())

        showContentAnimated(scroll(page))
    }

    private fun buildStowageTimePage() {
        val page = newPage()
        page.addView(button("BACK") { buildSettingPage() }, fullWidthWrap())
        page.addView(label("STOWAGE TIME"), fullWidthWrap())

        val stowageRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
            elevation = dp(2).toFloat()
        }
        val stowageValue = label(
            "${FloatingSettingsStore.getStowageTimeSeconds(activity)} sec",
            18f,
            Color.WHITE
        )
        val minus = button("−") {
            val next = (FloatingSettingsStore.getStowageTimeSeconds(activity) - 1)
                .coerceAtLeast(FloatingSettingsStore.MIN_STOWAGE_TIME_SECONDS)
            FloatingSettingsStore.setStowageTimeSeconds(activity, next)
            stowageValue.text = "$next sec"
        }
        val plus = button("+") {
            val next = (FloatingSettingsStore.getStowageTimeSeconds(activity) + 1)
                .coerceAtMost(FloatingSettingsStore.MAX_STOWAGE_TIME_SECONDS)
            FloatingSettingsStore.setStowageTimeSeconds(activity, next)
            stowageValue.text = "$next sec"
        }
        stowageRow.addView(minus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stowageRow.addView(stowageValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        stowageRow.addView(plus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(stowageRow, fullWidthWrap())
        page.addView(
            label(
                "Range: ${FloatingSettingsStore.MIN_STOWAGE_TIME_SECONDS}-${FloatingSettingsStore.MAX_STOWAGE_TIME_SECONDS} sec",
                14f,
                Color.LTGRAY
            ),
            fullWidthWrap()
        )
        showContentAnimated(scroll(page))
    }

    private fun screenAwakePanel(): View {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
            elevation = dp(2).toFloat()
        }
        panel.addView(label("KEEP SCREEN ON"), fullWidthWrap())
        val group = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val on = radio("ON")
        val off = radio("OFF")
        group.addView(on)
        group.addView(off)
        if (screenAwakeProvider()) on.isChecked = true else off.isChecked = true
        group.setOnCheckedChangeListener { _, id ->
            when (id) {
                on.id -> onScreenAwakeChanged(true)
                off.id -> onScreenAwakeChanged(false)
            }
        }
        panel.addView(group, fullWidthWrap())
        return panel
    }

    private fun newPage() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(24, 16, 24, 24)
        setBackgroundColor(Win98Style.FACE)
    }

    private fun button(textValue: String, click: () -> Unit) = Button(activity).apply {
        text = textValue
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(Win98Style.TEXT)
        background = Win98Style.buttonBackground(dp(2))
        elevation = 0f
        stateListAnimator = null
        minWidth = 0
        minHeight = 0
        setOnClickListener { click() }
    }

    private fun label(textValue: String, size: Float = 15f, color: Int = Color.WHITE) =
        TextView(activity).apply {
            text = textValue
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

    private fun compactStatusLabel(textValue: String, size: Float, color: Int) =
        TextView(activity).apply {
            text = textValue
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding(dp(8), 0, dp(8), 0)
            includeFontPadding = false
        }

    private fun statusLineParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = 0
        bottomMargin = 0
    }

    private fun topPageFullWidthWrap(first: Boolean = false) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        // Explicit physical-pixel gap requested for the Config top page.
        topMargin = if (first) 0 else 1
    }

    private fun radio(textValue: String) = RadioButton(activity).apply {
        id = View.generateViewId()
        text = textValue
        setTextColor(Color.WHITE)
    }

    private fun scroll(page: LinearLayout) = ScrollView(activity).apply {
        isFillViewport = true
        addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun fullWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        // Config items were visually cramped. Keep geometry/order unchanged, but
        // provide a small breathing gap between neighboring controls.
        topMargin = dp(5)
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
}
