package com.example.diazymouse.ui.screen

import android.animation.ArgbEvaluator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.input.MouseSensitivity
import com.example.diazymouse.settings.vibration.UiVibrationConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.UiVibrator
import com.example.diazymouse.ui.Win98Style
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** New EiryMouse draft UI: fixed top controls, touch pad, and A/B four-way sticks. */
class AllTouchDisplayArea(
    private val activity: ComponentActivity,
    @Suppress("UNUSED_PARAMETER") private val dimensions: UiDimensions,
    private val inputTransport: MainInputTransport,
    @Suppress("UNUSED_PARAMETER") private val onToggleKeyboardWithHanZen: () -> Boolean,
    private val onOpenConfig: () -> Unit = {},
    private val onConfigLongPress: () -> Unit = {},
    private val onFloating: () -> Unit = {},
    private val onFloatingLockChanged: (Boolean) -> Unit = {},
    private val onOperation: () -> Unit = {},
    private val onHelpOpened: () -> Unit = {},
    private val onHelpClosed: () -> Unit = {},
    private val onHelpActivity: () -> Unit = {}
) {
    enum class ConnectionVisualState { IDLE, CONNECTING, CONNECTED, ERROR }
    private enum class Dir { CENTER, UP, DOWN, LEFT, RIGHT }
    private data class StickTrack(
        val pointerId: Int,
        val side: String,
        val startX: Float,
        val startY: Float,
        val startTime: Long,
        var dir: Dir = Dir.CENTER,
        var dx: Float = 0f,
        var dy: Float = 0f
    )

    private var surface: FrameLayout? = null
    private var infoBar: TextView? = null
    private var infoBarMirror: TextView? = null
    private var blinkAnimator: ValueAnimator? = null
    private var floatingTransparencyActive = false
    private var lastState = ConnectionVisualState.IDLE
    private var configMode = false
    private var helpPopup: PopupWindow? = null
    private var helpOverlayVisible = false
    private var floatingButton: TextView? = null
    private var floatingUsed = false
    private var floatingLocked = false
    private var textDragLatched = false
    private var modifierOneShotMask: Byte = 0
    private var modifierLockedMask: Byte = 0
    private val modifierButtons = linkedMapOf<Byte, TextView>()
    private val modifierLockColor = Color.rgb(0, 168, 132) // dedicated emerald lock color
    private var stickTouchView: View? = null
    private var touchPadTouchView: View? = null
    private var infoIdleReset: Runnable? = null
    private val uiVibrator = UiVibrator(activity)
    private val density = activity.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val visualFillColors = java.util.WeakHashMap<View, Int>()
    private val buttonColorAnimators = java.util.WeakHashMap<View, ValueAnimator>()

    private fun dp(v: Int) = (v * density).roundToInt()

    /** Subtle hardware-like press feedback. It never changes layout or hit bounds. */
    private fun installPressAnimation(view: View) {
        fun pressSet(scale: Float, translationY: Float, durationMs: Long) = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, scale),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, translationY)
            )
            duration = durationMs
        }
        view.stateListAnimator = StateListAnimator().apply {
            // A tiny downward travel plus scale reduction reads as a physical button press
            // without changing layout, hit bounds, or any gesture assignment.
            addState(intArrayOf(android.R.attr.state_pressed), pressSet(0.955f, dp(1).toFloat(), 80L))
            addState(intArrayOf(), pressSet(1.0f, 0f, 125L))
        }
    }

    /** Fade between semantic state colors while keeping the Win98 bevel. */
    private fun animateButtonColor(view: TextView, targetFill: Int, stroke: Int, radiusDp: Int, targetText: Int) {
        val startFill = visualFillColors[view] ?: Win98Style.FACE
        visualFillColors[view] = targetFill
        // A quick second tap can reverse the modifier state before the previous color
        // animation has finished. Cancel that old animator first; otherwise its final
        // frame can repaint SHIFT/CTRL/ALT with the selected color after state is OFF.
        buttonColorAnimators.remove(view)?.cancel()
        val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startFill, targetFill).apply {
            duration = 200L
            addUpdateListener { animator ->
                // Keep the Win98 bevel for every semantic state; only the fill changes.
                view.background = Win98Style.raisedBackground(animator.animatedValue as Int, dp(2))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (buttonColorAnimators[view] === animation) buttonColorAnimators.remove(view)
                }
            })
        }
        buttonColorAnimators[view] = colorAnimator
        colorAnimator.start()
        view.animate().cancel()
        view.animate().alpha(0.86f).setDuration(75L).withEndAction {
            view.setTextColor(targetText)
            view.animate().alpha(1f).setDuration(120L).start()
        }.start()
    }

    fun addTo(root: FrameLayout): FrameLayout {
        val layer = FrameLayout(activity).apply {
            setBackgroundColor(Win98Style.FACE)
            isClickable = true
            isFocusable = true
        }
        surface = layer
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            buildLandscapeTopBar(layer)
        } else {
            buildTopBar(layer)
        }
        buildTouchPad(layer)
        applyConnectionVisualState(lastState)
        return layer
    }


    /** Landscape keyboard layout: controls are biased to both edges.
     * Left 2x3 = Floating/WIN/SHIFT/CTRL/ALT/CLEAR.
     * Right top row = Mouse/Config; the remaining four right slots are owned by
     * KeyboardUiController (Han/Zen, Muhenkan, Henkan, Enter).
     * Info is split into two wide panes that absorb the former C guard areas.
     */
    private fun buildLandscapeTopBar(parent: FrameLayout) {
        val h = dp(146)
        val cellH = dp(42)
        val gap = dp(4)

        fun makeButton(label: String, action: () -> Unit): TextView = TextView(activity).apply {
            text = label; textSize = 10.5f; gravity = Gravity.CENTER
            setTextColor(Win98Style.TEXT)
            background = Win98Style.buttonBackground(dp(2))
            isClickable = true
            elevation = dp(2).toFloat()
            visualFillColors[this] = Color.rgb(242,247,252)
            installPressAnimation(this)
            setOnClickListener { if (!configMode) { uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD); action() } }
        }
        fun place(v: View, left: Int, top: Int, w: Int, hh: Int = cellH) {
            parent.addView(v, FrameLayout.LayoutParams(w, hh).apply {
                gravity = Gravity.TOP or Gravity.START; leftMargin = left; topMargin = top
            })
        }
        val screenW = activity.resources.displayMetrics.widthPixels
        val margin = dp(10)
        val top = dp(8)
        val cNotchRadius = dp(64)
        val bankToInfoGap = dp(6)

        // The former black C guards are now absorbed into the two Info panes.
        // Preserve the old outer Info edges so the left/right button banks do not move,
        // while extending Info inward to within one physical pixel of the larger C notch.
        val centerX = screenW / 2
        val infoOuterDistance = dp(178)
        val cGapPx = 1
        val leftInfoX = centerX - infoOuterDistance
        val leftInfoRight = centerX - cNotchRadius - cGapPx
        val rightInfoX = centerX + cNotchRadius + cGapPx
        val rightInfoRight = centerX + infoOuterDistance
        val mergedInfoW = (leftInfoRight - leftInfoX).coerceAtLeast(dp(34))

        val edgeW = (leftInfoX - margin - bankToInfoGap).coerceAtLeast(dp(150))
        val cellW = (edgeW - gap) / 2

        val floating = makeButton("Floating") {
            if (floatingLocked) showInfo("Floating Lock") else { floatingUsed=true; updateFloatingButtonVisual(); showInfo("Floating"); onFloating() }
        }
        floatingButton = floating
        var floatingLong = false; var floatingTask: Runnable? = null
        floating.setOnTouchListener { _, e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { floatingLong=false; floatingTask=Runnable { if(!configMode){ floatingLong=true; floatingLocked=!floatingLocked; updateFloatingButtonVisual(); onFloatingLockChanged(floatingLocked); showInfo(if(floatingLocked) "Floating Lock" else "Floating Active") } }.also{handler.postDelayed(it,2000)}; false }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { floatingTask?.let{handler.removeCallbacks(it)}; floatingTask=null; if(floatingLong){floatingLong=false;true}else false }
                else -> floatingLong
            }
        }
        val win = makeButton("WIN") {
            val base=(modifierOneShotMask.toInt() or modifierLockedMask.toInt()).toByte()
            val withWin=(base.toInt() or HidKeyboardPacket.MOD_LEFT_GUI.toInt()).toByte()
            inputTransport.holdKeyboardModifiers(withWin); handler.postDelayed({ if(base.toInt()==0) inputTransport.releaseKeyboardModifiers() else inputTransport.holdKeyboardModifiers(base) },80); showInfo("Win")
        }
        place(floating, margin, top, cellW); place(win, margin+cellW+gap, top, cellW)

        fun modifier(label:String, mask:Byte, x:Int, y:Int) {
            val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            addModifierButton(row,label,mask)
            place(row,x,y,cellW)
        }
        modifier("SHIFT",HidKeyboardPacket.MOD_LEFT_SHIFT,margin,top+cellH+gap)
        modifier("CTRL",HidKeyboardPacket.MOD_LEFT_CTRL,margin+cellW+gap,top+cellH+gap)
        modifier("ALT",HidKeyboardPacket.MOD_LEFT_ALT,margin,top+(cellH+gap)*2)
        val clear=makeButton("CLEAR") { clearModifiers(); showInfo("Modifiers Clear") }
        place(clear,margin+cellW+gap,top+(cellH+gap)*2,cellW)

        // Two independent-looking Info panes mirror the same state/text.
        val leftInfo=makeButton("Info") { showInfo("Info") }
        val rightInfo=makeButton("Info") { showInfo("Info") }
        // Info behaves like a Win98 title/status bar rather than a modern raised app button.
        listOf(leftInfo, rightInfo).forEach {
            it.stateListAnimator = null
            it.elevation = 0f
            it.isAllCaps = false
            it.paint.isFakeBoldText = true
        }
        leftInfo.setOnLongClickListener { if(!configMode) showHelpOverlay(parent); true }
        rightInfo.setOnLongClickListener { if(!configMode) showHelpOverlay(parent); true }
        place(leftInfo,leftInfoX,top,mergedInfoW,h-dp(16))
        // Anchor the whole right side to the parent's END edge instead of deriving
        // its X coordinate from DisplayMetrics.  On devices with a landscape
        // navigation/system inset (e.g. Galaxy S10), DisplayMetrics and this root
        // view can have different usable origins, which pulled the right bank and
        // right Info pane toward the screen centre after relayout.
        val rightInfoW = (rightInfoRight-rightInfoX).coerceAtLeast(dp(34))
        val rightBankW = cellW * 2 + gap
        parent.addView(rightInfo, FrameLayout.LayoutParams(rightInfoW, h-dp(16)).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = margin + rightBankW + bankToInfoGap
            topMargin = top
        })
        infoBar=leftInfo; infoBarMirror=rightInfo

        val mouse=makeButton("Mouse") { showInfo("Mouse"); onOperation() }
        val config=makeButton("Config") { showInfo("Config"); onOpenConfig() }
        config.setOnLongClickListener { if(!configMode){uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD);onConfigLongPress()}; true }
        parent.addView(mouse, FrameLayout.LayoutParams(cellW, cellH).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = margin + cellW + gap
            topMargin = top
        })
        parent.addView(config, FrameLayout.LayoutParams(cellW, cellH).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = margin
            topMargin = top
        })
    }

    private fun buildTopBar(parent: FrameLayout) {
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Win98Style.panelBackground(dp(2))
            elevation = dp(2).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        parent.addView(top, FrameLayout.LayoutParams(-1, dp(124)).apply {
            gravity = Gravity.TOP
            marginStart = dp(10); marginEnd = dp(10); topMargin = dp(8)
        })

        fun row(): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }.also { top.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }

        fun button(row: LinearLayout, label: String, weight: Float = 1f, action: () -> Unit): TextView {
            val v = TextView(activity).apply {
                text = label
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(Win98Style.TEXT)
                background = Win98Style.buttonBackground(dp(2))
                isClickable = true
                elevation = dp(2).toFloat()
                visualFillColors[this] = Win98Style.FACE
                installPressAnimation(this)
                setOnClickListener { if (!configMode) { uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD); action() } }
            }
            row.addView(v, LinearLayout.LayoutParams(0, -1, weight).apply {
                marginStart = dp(2); marginEnd = dp(2); topMargin = dp(2); bottomMargin = dp(2)
            })
            return v
        }

        val first = row()
        val floating = button(first, "Floating", 1.0f) {
            if (floatingLocked) showInfo("Floating Lock") else {
                floatingUsed = true; updateFloatingButtonVisual(); showInfo("Floating"); onFloating()
            }
        }
        floatingButton = floating
        var floatingLongPressTriggered = false
        var floatingLongPressRunnable: Runnable? = null
        floating.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    floatingLongPressTriggered = false
                    floatingLongPressRunnable?.let { handler.removeCallbacks(it) }
                    floatingLongPressRunnable = Runnable {
                        if (!configMode) {
                            floatingLongPressTriggered = true
                            uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD)
                            floatingLocked = !floatingLocked
                            updateFloatingButtonVisual(); onFloatingLockChanged(floatingLocked)
                            showInfo(if (floatingLocked) "Floating Lock" else "Floating Active")
                        }
                    }.also { handler.postDelayed(it, 2_000L) }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    floatingLongPressRunnable?.let { handler.removeCallbacks(it) }; floatingLongPressRunnable = null
                    if (floatingLongPressTriggered) { floatingLongPressTriggered = false; true } else false
                }
                else -> floatingLongPressTriggered
            }
        }

        button(first, "WIN", 0.72f) {
            // Standalone Windows key. Holding GUI then releasing opens Start on Windows.
            val base = (modifierOneShotMask.toInt() or modifierLockedMask.toInt()).toByte()
            val withWin = (base.toInt() or HidKeyboardPacket.MOD_LEFT_GUI.toInt()).toByte()
            inputTransport.holdKeyboardModifiers(withWin)
            handler.postDelayed({
                if (base.toInt() == 0) inputTransport.releaseKeyboardModifiers()
                else inputTransport.holdKeyboardModifiers(base)
            }, 80L)
            showInfo("Win")
        }
        val info = button(first, "Info", 1.56f) { showInfo("Info") }
        infoBar = info
        info.stateListAnimator = null
        info.elevation = 0f
        info.isAllCaps = false
        info.paint.isFakeBoldText = true
        info.setOnLongClickListener { if (!configMode) showHelpOverlay(parent); true }
        button(first, "Mouse", 0.72f) { showInfo("Mouse"); onOperation() }
        val config = button(first, "Config", 1.0f) { showInfo("Config"); onOpenConfig() }
        config.setOnLongClickListener {
            if (!configMode) { uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD); onConfigLongPress() }
            true
        }

        val second = row()
        addModifierButton(second, "SHIFT", HidKeyboardPacket.MOD_LEFT_SHIFT)
        addModifierButton(second, "CTRL", HidKeyboardPacket.MOD_LEFT_CTRL)
        addModifierButton(second, "ALT", HidKeyboardPacket.MOD_LEFT_ALT)
        button(second, "CLEAR") { clearModifiers(); showInfo("Modifiers Clear") }
    }

    private fun addModifierButton(row: LinearLayout, label: String, mask: Byte) {
        val v = TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Win98Style.TEXT)
            background = Win98Style.buttonBackground(dp(2))
            isClickable = true
            visualFillColors[this] = Win98Style.FACE
            installPressAnimation(this)
        }
        row.addView(v, LinearLayout.LayoutParams(0, -1, 1f).apply {
            marginStart = dp(2); marginEnd = dp(2); topMargin = dp(2); bottomMargin = dp(2)
        })
        modifierButtons[mask] = v
        var longTriggered = false
        var holdTask: Runnable? = null
        v.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (configMode) return@setOnTouchListener true
                    // This listener consumes the touch for tap/hold modifier logic, so mirror
                    // the normal Button pressed state explicitly. That lets SHIFT/CTRL/ALT
                    // use exactly the same press StateListAnimator as CLEAR.
                    v.isPressed = true
                    longTriggered = false
                    holdTask = Runnable {
                        longTriggered = true
                        modifierOneShotMask = (modifierOneShotMask.toInt() and mask.toInt().inv()).toByte()
                        modifierLockedMask = (modifierLockedMask.toInt() xor mask.toInt()).toByte()
                        applyModifierState()
                        showInfo(if ((modifierLockedMask.toInt() and mask.toInt()) != 0) "$label LOCK" else "$label OFF")
                    }.also { handler.postDelayed(it, 1_000L) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    holdTask?.let { handler.removeCallbacks(it) }; holdTask = null
                    if (!longTriggered) {
                        modifierOneShotMask = (modifierOneShotMask.toInt() xor mask.toInt()).toByte()
                        applyModifierState(); showInfo(if ((modifierOneShotMask.toInt() and mask.toInt()) != 0) "$label NEXT" else "$label OFF")
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    holdTask?.let { handler.removeCallbacks(it) }; holdTask = null; true
                }
                else -> true
            }
        }
    }

    private fun applyModifierState() {
        val effective = (modifierOneShotMask.toInt() or modifierLockedMask.toInt()).toByte()
        if (effective.toInt() == 0) inputTransport.releaseKeyboardModifiers() else inputTransport.holdKeyboardModifiers(effective)
        modifierButtons.forEach { (mask, view) ->
            val locked = (modifierLockedMask.toInt() and mask.toInt()) != 0
            val selected = (modifierOneShotMask.toInt() and mask.toInt()) != 0
            val targetFill: Int
            val targetStroke: Int
            val targetText: Int
            when {
                locked -> { targetFill = modifierLockColor; targetStroke = Color.rgb(0, 110, 88); targetText = Color.WHITE }
                selected -> { targetFill = Color.rgb(198, 238, 224); targetStroke = Color.rgb(80, 170, 140); targetText = Color.rgb(24, 52, 78) }
                else -> { targetFill = Win98Style.FACE; targetStroke = Win98Style.SHADOW; targetText = Win98Style.TEXT }
            }
            animateButtonColor(view, targetFill, targetStroke, 9, targetText)
        }
    }

    private fun consumeOneShotModifiers() {
        if (modifierOneShotMask.toInt() == 0) return
        modifierOneShotMask = 0
        applyModifierState()
    }

    private fun clearModifiers() {
        modifierOneShotMask = 0; modifierLockedMask = 0
        inputTransport.releaseKeyboardModifiers(); applyModifierState()
    }

    private var cSelectAccumX = 0
    private var cSelectAccumY = 0

    fun startCLeverTextDrag() {
        if (textDragLatched) return
        textDragLatched = true
        cSelectAccumX = 0; cSelectAccumY = 0
        showInfo("Text Select ON")
    }

    fun moveCLeverTextDrag(dx: Int, dy: Int) {
        if (!textDragLatched) return
        // Text Select moves the caret with Shift+Arrow; the mouse pointer never moves.
        cSelectAccumX += dx; cSelectAccumY += dy
        val step = dp(12).coerceAtLeast(8)
        while (kotlin.math.abs(cSelectAccumX) >= step) {
            inputTransport.pressHidKey(if (cSelectAccumX > 0) HidKeyboardPacket.KEY_RIGHT_ARROW else HidKeyboardPacket.KEY_LEFT_ARROW, HidKeyboardPacket.MOD_LEFT_SHIFT)
            cSelectAccumX += if (cSelectAccumX > 0) -step else step
        }
        while (kotlin.math.abs(cSelectAccumY) >= step) {
            inputTransport.pressHidKey(if (cSelectAccumY > 0) HidKeyboardPacket.KEY_DOWN_ARROW else HidKeyboardPacket.KEY_UP_ARROW, HidKeyboardPacket.MOD_LEFT_SHIFT)
            cSelectAccumY += if (cSelectAccumY > 0) -step else step
        }
    }

    fun endCLeverTextDrag() {
        if (!textDragLatched) return
        textDragLatched = false
        showInfo("Text Selected")
    }

    private fun buildTouchPad(parent: FrameLayout) {
        val pad = TouchSurfaceView(activity).apply {
            background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
            isClickable = true
            isFocusable = true
        }
        touchPadTouchView = pad
        val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val touchPadTop = if (landscape) dp(160) else dp(144)
        parent.addView(pad, FrameLayout.LayoutParams(-1, -1).apply {
            gravity = Gravity.TOP or Gravity.START
            // Landscape is an explicit full-width hit surface.  Do not leave
            // side margins here: the visible lower TouchPad and its hit area
            // must be the same rectangle across the entire screen width.
            marginStart = if (landscape) 0 else dp(10)
            marginEnd = if (landscape) 0 else dp(10)
            topMargin = touchPadTop
            bottomMargin = if (landscape) 0 else dp(70)
        })

        // Portrait only: extend just the center of the TouchPad downward behind the C stick.
        // This visually keeps the entire circular C stick on the TouchPad surface without
        // moving the existing Han/Zen/Muhenkan/Henkan/Enter row or changing any input logic.
        // The extension deliberately has no click/touch handler; the C lever remains the
        // foreground input target and the surrounding button behavior stays unchanged.
        if (!landscape) {
            val cBridge = View(activity).apply {
                setBackgroundColor(Color.rgb(242, 242, 242))
                isClickable = false
                isFocusable = false
            }
            parent.addView(
                cBridge,
                FrameLayout.LayoutParams(dp(122), dp(44)).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    // TouchPad currently ends 70dp above the parent bottom.
                    // Bridge spans from 74dp to 30dp, covering the C stick's
                    // lowest edge (36dp) with a small safety margin.
                    bottomMargin = dp(30)
                }
            )
        }

        if (landscape) {
            // MATCH_PARENT + topMargin can be measured as a full-parent child
            // and then clipped by FrameLayout. Make the actual hit rectangle
            // explicit after the parent has its final Landscape/IME-resized
            // dimensions.
            //
            // Important for newer Android/vendor builds (including TORQUE G06):
            // the IME safe-area may change AFTER this screen was first laid out.
            // Recalculate the Landscape TouchPad every time the parent bounds
            // change so A/B sticks never keep the pre-IME bottom position.
            fun updateLandscapeTouchPadBounds() {
                if (parent.width <= 0 || parent.height <= 0) return
                val availableHeight = (parent.height - touchPadTop).coerceAtLeast(1)
                val lp = pad.layoutParams as FrameLayout.LayoutParams
                val changed =
                    lp.width != FrameLayout.LayoutParams.MATCH_PARENT ||
                    lp.height != availableHeight ||
                    lp.topMargin != touchPadTop ||
                    lp.bottomMargin != 0 ||
                    lp.leftMargin != 0 ||
                    lp.rightMargin != 0

                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = availableHeight
                lp.gravity = Gravity.TOP or Gravity.START
                lp.leftMargin = 0
                lp.rightMargin = 0
                lp.marginStart = 0
                lp.marginEnd = 0
                lp.topMargin = touchPadTop
                lp.bottomMargin = 0

                if (changed) {
                    pad.layoutParams = lp
                    pad.requestLayout()
                    Log.i(
                        "EIRY_TOUCH",
                        "Landscape TouchPad bounds updated for IME/safe-area: " +
                            "parent=${parent.width}x${parent.height} top=$touchPadTop " +
                            "size=${parent.width}x$availableHeight"
                    )
                }
            }

            // Initial layout.
            parent.post { updateLandscapeTouchPadBounds() }

            // Re-run after IME appearance/disappearance, rotation inset changes,
            // navigation-mode changes, split-screen resizing, etc.
            parent.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newWidth = right - left
                val newHeight = bottom - top
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                if (newWidth != oldWidth || newHeight != oldHeight) {
                    parent.post { updateLandscapeTouchPadBounds() }
                }
            }
        }

        val title = TextView(activity).apply {
            text = "TouchPad\n1 finger / 2 finger gestures"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Win98Style.TITLE_BLUE)
            isClickable = false
        }
        pad.addView(title, FrameLayout.LayoutParams(-1, dp(70)).apply { gravity = Gravity.TOP; topMargin = dp(14) })

        val sticks = DualStickView()
        stickTouchView = sticks
        val stickHeight = dp(152)
        pad.addView(sticks, FrameLayout.LayoutParams(-1, stickHeight).apply {
            gravity = Gravity.BOTTOM
            marginStart = dp(10)
            marginEnd = dp(10)
            // Portrait keeps the established position. Landscape used to reserve a
            // fixed 80dp below A/B. On short-wide phones (for example OPPO-class
            // displays after system-bar/inset subtraction), 152dp + 80dp can exceed
            // the real TouchPad height and push the top of the sticks outside the pad.
            // Start at zero and calculate the largest safe margin after measurement.
            bottomMargin = if (landscape) 0 else dp(10)
        })

        if (landscape) {
            fun updateLandscapeStickBottomMargin() {
                if (pad.height <= 0) return
                val lp = sticks.layoutParams as FrameLayout.LayoutParams
                val desiredMargin = dp(80)
                val safety = dp(4)
                // Preserve the old 80dp position on tall screens, but automatically
                // reduce it when the Landscape TouchPad is shallow. This keeps the
                // complete 152dp A/B stick view inside the visible TouchPad instead
                // of clipping it through the upper separator.
                val maxMarginThatFits = (pad.height - stickHeight - safety).coerceAtLeast(0)
                val safeMargin = minOf(desiredMargin, maxMarginThatFits)
                if (lp.bottomMargin != safeMargin) {
                    lp.bottomMargin = safeMargin
                    sticks.layoutParams = lp
                    sticks.requestLayout()
                    Log.i(
                        "EIRY_TOUCH",
                        "Landscape stick margin adjusted: padH=${pad.height} " +
                            "stickH=$stickHeight bottom=$safeMargin desired=$desiredMargin"
                    )
                }
            }

            pad.post { updateLandscapeStickBottomMargin() }
            pad.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newWidth = right - left
                val newHeight = bottom - top
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                if (newWidth != oldWidth || newHeight != oldHeight) {
                    pad.post { updateLandscapeStickBottomMargin() }
                }
            }
        }
    }

    fun isStickTouch(rawX: Float, rawY: Float): Boolean {
        val target = stickTouchView as? DualStickView ?: return false
        if (!target.isShown) return false
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val localX = rawX - location[0]
        val localY = rawY - location[1]
        // Treat only the actual circular A/B stick areas as stick input.
        // The transparent rectangular space between/around the sticks remains TouchPad.
        return target.isStickHit(localX, localY)
    }

    fun isTouchPadTouch(rawX: Float, rawY: Float): Boolean {
        val target = touchPadTouchView ?: return false
        if (!target.isShown) return false
        val rect = android.graphics.Rect()
        target.getGlobalVisibleRect(rect)
        return rect.contains(rawX.toInt(), rawY.toInt())
    }

    private fun executeStick(which: String, dir: Dir, longPress: Boolean = false) {
        if (configMode) return
        uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD)
        val label = when (which to dir) {
            "A" to Dir.UP -> "Redo"
            "A" to Dir.DOWN -> "Undo"
            "A" to Dir.LEFT -> "Back"
            "A" to Dir.RIGHT -> "Forward"
            "B" to Dir.UP -> "Select All"
            "B" to Dir.DOWN -> "Delete"
            "B" to Dir.LEFT -> if (longPress) "Cut" else "Copy"
            "B" to Dir.RIGHT -> "Paste"
            else -> return
        }
        showInfo(label)
        when (which to dir) {
            "A" to Dir.UP -> inputTransport.redo()
            "A" to Dir.DOWN -> inputTransport.undo()
            "A" to Dir.LEFT -> inputTransport.back()
            "A" to Dir.RIGHT -> inputTransport.forward()
            "B" to Dir.UP -> inputTransport.selectAll()
            "B" to Dir.DOWN -> inputTransport.deleteKey()
            "B" to Dir.LEFT -> if (longPress) inputTransport.cut() else inputTransport.copy()
            "B" to Dir.RIGHT -> inputTransport.paste()
        }
    }

    private fun executeDual(dir: Dir?) {
        if (configMode) return
        if (dir == null) {
            showInfo("Invalid")
            return
        }
        uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD)
        showInfo("A+B ${dir.name}")
        when (dir) {
            Dir.CENTER -> return
            Dir.UP -> inputTransport.mute()
            Dir.DOWN -> inputTransport.refreshF5()
            Dir.LEFT -> inputTransport.selectAll()
            Dir.RIGHT -> inputTransport.deleteKey()
        }
    }

    private fun runPointerShake() {
        // Relative HID movement only: equal positive/negative totals aim to return
        // the pointer to its starting position without requiring any PC software.
        val steps = intArrayOf(35, -70, 70, -70, 35)
        steps.forEachIndexed { index, dx ->
            handler.postDelayed({ inputTransport.move(dx, 0) }, index * 100L)
        }
    }

    /** One shared view owns both sticks so true A+B multi-touch can be judged reliably. */
    private inner class DualStickView : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tracks = linkedMapOf<Int, StickTrack>()
        private var dualActive = false
        private var dualCommitted = false
        private var pointerShakeArmed = false
        private var lastPointerShakeMs = 0L
        private val pointerShakeRunnable = Runnable {
            val a = tracks.values.firstOrNull { it.side == "A" }
            val b = tracks.values.firstOrNull { it.side == "B" }
            val now = android.os.SystemClock.uptimeMillis()
            if (pointerShakeArmed && a?.dir == Dir.CENTER && b?.dir == Dir.CENTER &&
                now - lastPointerShakeMs >= 2_000L) {
                dualCommitted = true
                pointerShakeArmed = false
                lastPointerShakeMs = now
                showInfo("Pointer Shake")
                uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD)
                runPointerShake()
            }
        }
        private val dead = dp(18).toFloat()
        private val maxMove = dp(30).toFloat()
        private val releaseOffsets = mutableMapOf("A" to Pair(0f, 0f), "B" to Pair(0f, 0f))
        private val releaseAnimators = mutableMapOf<String, ValueAnimator>()

        private fun animateStickHome(side: String, fromX: Float, fromY: Float) {
            releaseAnimators.remove(side)?.cancel()
            releaseOffsets[side] = Pair(fromX, fromY)
            releaseAnimators[side] = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 125L
                addUpdateListener { a ->
                    val t = a.animatedFraction
                    val eased = 1f - (1f - t) * (1f - t)
                    releaseOffsets[side] = Pair(fromX * (1f - eased), fromY * (1f - eased))
                    invalidate()
                }
                doOnEndCompat {
                    releaseOffsets[side] = Pair(0f, 0f)
                    releaseAnimators.remove(side)
                    invalidate()
                }
                start()
            }
        }

        private fun ValueAnimator.doOnEndCompat(block: () -> Unit) {
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = block()
                override fun onAnimationCancel(animation: android.animation.Animator) = block()
            })
        }

        // Keep both sticks 5% farther inward than the previous layout to prevent clipping.
        private fun centerX(side: String) = if (side == "A") width * 0.20f else width * 0.80f
        private fun centerY() = height * 0.48f
        private fun sideFor(x: Float) = if (x < width / 2f) "A" else "B"

        /** True only inside the actual visible A/B stick circles, not the full shared view. */
        fun isStickHit(x: Float, y: Float): Boolean {
            val hitRadius = dp(62).toFloat()
            val dy = y - centerY()
            val ax = x - centerX("A")
            val bx = x - centerX("B")
            return hypot(ax.toDouble(), dy.toDouble()) <= hitRadius ||
                hypot(bx.toDouble(), dy.toDouble()) <= hitRadius
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            drawOne(c, "A", Color.rgb(231, 111, 81))
            drawOne(c, "B", Color.rgb(78, 168, 222))
        }

        private fun drawOne(c: Canvas, side: String, accent: Int) {
            val cx=centerX(side); val cy=centerY(); val t=tracks.values.firstOrNull{it.side==side}
            // No circular base/well under the A/B stick.
            // The colored knob is drawn directly on the TouchPad surface.
            paint.style = Paint.Style.FILL
            paint.shader = null

            paint.color=Win98Style.TEXT; paint.textAlign=Paint.Align.CENTER; paint.textSize=dp(10).toFloat()
            val upLabel = if (side == "A") "Redo" else "Select All"
            val downLabel = if (side == "A") "Undo" else "Delete"
            val leftLabel = if (side == "A") "Back" else "Copy / Cut"
            val rightLabel = if (side == "A") "Forward" else "Paste"
            c.drawText(upLabel,cx,cy-dp(48),paint); c.drawText(downLabel,cx,cy+dp(57),paint)
            c.drawText(leftLabel,cx-dp(53),cy+dp(5),paint); c.drawText(rightLabel,cx+dp(53),cy+dp(5),paint)

            val visualOffset = if (t != null) Pair(t.dx, t.dy) else (releaseOffsets[side] ?: Pair(0f, 0f))
            val kx = cx + visualOffset.first
            val ky = cy + visualOffset.second
            val knobR = dp(30).toFloat()
            // Round, more strongly three-dimensional cap; no black circular base/shadow.
            val hi = Color.rgb(
                (Color.red(accent) + 255).coerceAtMost(510) / 2,
                (Color.green(accent) + 255).coerceAtMost(510) / 2,
                (Color.blue(accent) + 255).coerceAtMost(510) / 2
            )
            val lo = Color.rgb(
                (Color.red(accent) * 45 / 100),
                (Color.green(accent) * 45 / 100),
                (Color.blue(accent) * 45 / 100)
            )
            paint.shader = RadialGradient(
                kx - knobR * 0.35f, ky - knobR * 0.42f, knobR * 1.45f,
                intArrayOf(Color.WHITE, hi, accent, lo),
                floatArrayOf(0f, 0.18f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(kx, ky, knobR, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.argb(190, 0, 0, 0)
            c.drawCircle(kx, ky, knobR, paint)
            paint.style = Paint.Style.FILL
            paint.color=Color.WHITE; paint.textSize=dp(24).toFloat(); paint.isFakeBoldText=true
            c.drawText(side,kx,ky+dp(8),paint); paint.isFakeBoldText=false
        }

        private fun addPointer(e: MotionEvent, index: Int) {
            val id=e.getPointerId(index); val x=e.getX(index); val y=e.getY(index); val side=sideFor(x)
            // A stick gesture is valid only when it starts on the center knob.
            if (hypot((x-centerX(side)).toDouble(), (y-centerY()).toDouble()) > dp(36)) return
            if(tracks.values.any{it.side==side}) return
            releaseAnimators.remove(side)?.cancel()
            releaseOffsets[side] = Pair(0f, 0f)
            tracks[id]=StickTrack(id,side,x,y,e.eventTime)
            if(tracks.values.map{it.side}.toSet().size==2) {
                dualActive=true
                pointerShakeArmed = true
                handler.removeCallbacks(pointerShakeRunnable)
                handler.postDelayed(pointerShakeRunnable, 2_000L)
            }
        }
        private fun updatePointer(e: MotionEvent, index: Int) {
            val id=e.getPointerId(index); val t=tracks[id]?:return
            val dx=e.getX(index)-t.startX; val dy=e.getY(index)-t.startY; val dist=hypot(dx.toDouble(),dy.toDouble()).toFloat()
            t.dir=if(dist<dead) Dir.CENTER else if(abs(dx)>abs(dy)) { if(dx<0) Dir.LEFT else Dir.RIGHT } else { if(dy<0) Dir.UP else Dir.DOWN }
            val scale=if(dist>maxMove && dist>0) maxMove/dist else 1f; t.dx=dx*scale; t.dy=dy*scale
            val a=tracks.values.firstOrNull{it.side=="A"}; val b=tracks.values.firstOrNull{it.side=="B"}
            if (a != null && b != null) {
                if (a.dir != Dir.CENTER || b.dir != Dir.CENTER) {
                    pointerShakeArmed = false
                    handler.removeCallbacks(pointerShakeRunnable)
                }
                val preview = when {
                    a.dir==Dir.LEFT && b.dir==Dir.RIGHT -> "Find"
                    a.dir==Dir.RIGHT && b.dir==Dir.LEFT -> "Esc"
                    a.dir==Dir.UP && b.dir==Dir.DOWN -> "Show Desktop"
                    a.dir==Dir.DOWN && b.dir==Dir.UP -> "Show Desktop / Return"
                    a.dir==Dir.DOWN && b.dir==Dir.DOWN -> "Refresh (F5)"
                    a.dir==Dir.UP && b.dir==Dir.UP -> "Mute"
                    else -> "Invalid"
                }
                showInfo(preview)
            } else {
                val name = when(t.side to t.dir) {
                    "A" to Dir.UP -> "Redo"; "A" to Dir.DOWN -> "Undo"; "A" to Dir.LEFT -> "Back"; "A" to Dir.RIGHT -> "Forward"
                    "B" to Dir.UP -> "Select All"; "B" to Dir.DOWN -> "Delete"; "B" to Dir.LEFT -> "Copy / Cut"; "B" to Dir.RIGHT -> "Paste"
                    else -> "${t.side} Center"
                }
                showInfo(name)
            }
        }
        private fun commitDual() {
            if(dualCommitted) return
            dualCommitted=true
            val a=tracks.values.firstOrNull{it.side=="A"}; val b=tracks.values.firstOrNull{it.side=="B"}
            if (a == null || b == null) return
            when {
                a.dir==Dir.LEFT && b.dir==Dir.RIGHT -> inputTransport.find()
                a.dir==Dir.RIGHT && b.dir==Dir.LEFT -> inputTransport.escapeKey()
                a.dir==Dir.UP && b.dir==Dir.DOWN -> inputTransport.showDesktop()
                a.dir==Dir.DOWN && b.dir==Dir.UP -> inputTransport.showDesktop()
                a.dir==Dir.DOWN && b.dir==Dir.DOWN -> inputTransport.refreshF5()
                a.dir==Dir.UP && b.dir==Dir.UP -> inputTransport.mute()
                else -> { showInfo("Invalid"); return }
            }
            uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if(configMode) return true
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN -> { handler.removeCallbacks(pointerShakeRunnable); pointerShakeArmed=false; tracks.clear(); dualActive=false; dualCommitted=false; addPointer(e,e.actionIndex); invalidate(); return true }
                MotionEvent.ACTION_POINTER_DOWN -> { addPointer(e,e.actionIndex); for(i in 0 until e.pointerCount) updatePointer(e,i); invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { for(i in 0 until e.pointerCount) updatePointer(e,i); invalidate(); return true }
                MotionEvent.ACTION_POINTER_UP -> {
                    handler.removeCallbacks(pointerShakeRunnable); pointerShakeArmed=false
                    for(i in 0 until e.pointerCount) updatePointer(e,i)
                    if(dualActive) commitDual()
                    tracks[e.getPointerId(e.actionIndex)]?.let { animateStickHome(it.side, it.dx, it.dy) }
                    tracks.remove(e.getPointerId(e.actionIndex)); invalidate(); return true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(pointerShakeRunnable); pointerShakeArmed=false
                    updatePointer(e,e.actionIndex)
                    if(dualActive) commitDual() else tracks.values.firstOrNull()?.let{ if (it.dir != Dir.CENTER) executeStick(it.side,it.dir,e.eventTime-it.startTime >= 1_000L) }
                    tracks.values.toList().forEach { animateStickHome(it.side, it.dx, it.dy) }
                    tracks.clear(); invalidate(); return true
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(pointerShakeRunnable); pointerShakeArmed=false; tracks.values.toList().forEach { animateStickHome(it.side, it.dx, it.dy) }; tracks.clear(); dualActive=false; invalidate(); return true }
            }
            return true
        }
    }

    private inner class TouchSurfaceView(activity: ComponentActivity) : FrameLayout(activity) {
        private val slop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
        private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
        private var downTime = 0L; private var lastTapTime = 0L
        private var moved = false; private var dragActive = false; private var twoFinger = false
        private var secondTapHoldCandidate = false
        private var twoStartX = 0f; private var twoStartY = 0f; private var pinchStart = 0f
        private var twoLastX = 0f; private var twoLastY = 0f; private var pinchLast = 0f
        private var twoDownTime = 0L; private var lastTwoTapTime = 0L; private var lastTwoTapDuration = 0L
        // Single long-hold drag is intentionally disabled. Drag starts only
        // when the second press of a double tap is held and moved.
        private val mouseSensitivity = MouseSensitivity(activity)

        private var downOnDualStick = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            // DualStickView spans almost the full width for A+B multi-touch, but only
            // the two visible circular stick areas may capture the gesture.
            // All transparent space between/around A/B belongs to the TouchPad.
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                val child = childUnder(ev.x, ev.y)
                downOnDualStick = if (child is DualStickView) {
                    child.isStickHit(ev.x - child.left, ev.y - child.top)
                } else {
                    false
                }
            }
            val intercept = !downOnDualStick
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                downOnDualStick = false
            }
            return intercept
        }

        private fun childUnder(x: Float, y: Float): View? {
            for (i in childCount - 1 downTo 0) {
                val c = getChildAt(i); val r = RectF(c.left.toFloat(), c.top.toFloat(), c.right.toFloat(), c.bottom.toFloat())
                if (r.contains(x, y)) return c
            }
            return null
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (configMode) {
                Log.v("EIRY_TOUCH", "TouchPad ignored: configMode action=${e.actionMasked}")
                return true
            }
            if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                Log.v("EIRY_TOUCH", "TouchPad action=${e.actionMasked} x=${e.x.toInt()} y=${e.y.toInt()} landscape=${activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
            }
            // While C-lever text selection is active, only C-lever movement may
            // extend the selection. Ignore TouchPad input until C is released.
            if (textDragLatched) return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; downTime = e.eventTime
                    moved = false; twoFinger = false; dragActive = false
                    secondTapHoldCandidate = lastTapTime != 0L && e.eventTime - lastTapTime < 280L
                    uiVibrator.vibrate(UiVibrationConfig.TOUCH_PAD); return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                    secondTapHoldCandidate = false; twoFinger = true; moved = false
                    twoStartX = (e.getX(0)+e.getX(1))/2f; twoStartY = (e.getY(0)+e.getY(1))/2f
                    pinchStart = hypot((e.getX(0)-e.getX(1)).toDouble(), (e.getY(0)-e.getY(1)).toDouble()).toFloat()
                    twoLastX=twoStartX; twoLastY=twoStartY; pinchLast=pinchStart; twoDownTime=e.eventTime; showInfo("Info"); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (twoFinger && e.pointerCount >= 2) return handleTwoMove(e)
                    if (e.pointerCount != 1) return true
                    val totalDx = e.x-downX; val totalDy=e.y-downY
                    if (hypot(totalDx.toDouble(), totalDy.toDouble()) > slop) {
                        moved = true
                        // Double-tap + move: press and hold the left mouse button.
                        // The PC decides whether the object under the pointer is draggable.
                        if (secondTapHoldCandidate && !dragActive && !textDragLatched) {
                            inputTransport.leftDown()
                            dragActive = true
                            showInfo("Drag ON")
                        }
                    }
                    val rawDx=e.x-lastX; val rawDy=e.y-lastY; lastX=e.x; lastY=e.y
                    if (dragActive || textDragLatched || moved) {
                        val distance=hypot(rawDx.toDouble(),rawDy.toDouble()).toFloat(); val mult=mouseSensitivity.multiplier(distance)
                        val dx=mouseSensitivity.apply(rawDx,mult); val dy=mouseSensitivity.apply(rawDy,mult)
                        if (dx!=0 || dy!=0) {
                            Log.v("EIRY_TOUCH", "TouchPad move dx=$dx dy=$dy")
                            inputTransport.move(dx,dy)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> return true
                MotionEvent.ACTION_UP -> {
                    if (dragActive) { inputTransport.leftUp(); showInfo("Drag OFF"); dragActive=false; secondTapHoldCandidate=false; lastTapTime=0L; return true }
                    if (twoFinger) { finishTwoGesture(e); twoFinger=false; return true }
                    val dx=e.x-downX; val dy=e.y-downY; val distance=hypot(dx.toDouble(),dy.toDouble()).toFloat()
                    if (distance < slop*1.4f) {
                        if (secondTapHoldCandidate || e.eventTime-lastTapTime < 280L) {
                            // A double tap without movement remains a normal double click.
                            // The first tap already sent one click, so send the second click here.
                            if (!textDragLatched) { inputTransport.leftClick(); consumeOneShotModifiers(); showInfo("Double click") }
                            lastTapTime=0L
                            secondTapHoldCandidate=false
                        } else {
                            if (!textDragLatched) { inputTransport.leftClick(); consumeOneShotModifiers(); showInfo("Left click") }
                            lastTapTime=e.eventTime
                        }
                    } else if (abs(dy) > abs(dx) && abs(dy) > dp(42)) {
                        inputTransport.scroll(if (dy < 0) 4 else -4); showInfo(if (dy < 0) "Scroll up" else "Scroll down")
                    } else if (abs(dx) > dp(42)) {
                        inputTransport.horizontalScroll(if (dx < 0) -4 else 4); showInfo(if (dx < 0) "Scroll left" else "Scroll right")
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { if (dragActive) inputTransport.leftUp(); dragActive=false; secondTapHoldCandidate=false; twoFinger=false; return true }
            }
            return true
        }

        private fun handleTwoMove(e: MotionEvent): Boolean {
            val cx=(e.getX(0)+e.getX(1))/2f; val cy=(e.getY(0)+e.getY(1))/2f
            val dist=hypot((e.getX(0)-e.getX(1)).toDouble(),(e.getY(0)-e.getY(1)).toDouble()).toFloat()
            twoLastX=cx; twoLastY=cy; pinchLast=dist
            val dx=cx-twoStartX; val dy=cy-twoStartY; val pinchDelta=dist-pinchStart
            if (abs(pinchDelta)>dp(16)) showInfo(if(pinchDelta>0) "Zoom In" else "Zoom Out")
            else if (abs(dx)>abs(dy) && abs(dx)>dp(10)) showInfo(if(dx<0) "Horizontal Scroll Left" else "Horizontal Scroll Right")
            else if (abs(dy)>dp(10)) showInfo(if(dy<0) "Scroll Up" else "Scroll Down")
            if (abs(pinchDelta) > dp(30)) moved=true
            if (hypot(dx.toDouble(),dy.toDouble()) > dp(35)) moved=true
            return true
        }

        private fun finishTwoGesture(e: MotionEvent) {
            val dx=twoLastX-twoStartX; val dy=twoLastY-twoStartY
            val duration=e.eventTime-twoDownTime
            if (!moved && abs(dx)<dp(18) && abs(dy)<dp(18) && abs(pinchLast-pinchStart)<dp(18)) {
                if (e.eventTime-lastTwoTapTime < 420L) {
                    if (max(duration,lastTwoTapDuration) >= 260L) { inputTransport.middleClick(); consumeOneShotModifiers(); showInfo("Center Click") }
                    else { inputTransport.rightClick(); consumeOneShotModifiers(); showInfo("Right Click") }
                    lastTwoTapTime=0L
                } else { lastTwoTapTime=e.eventTime; lastTwoTapDuration=duration; showInfo("Info") }
                return
            }
            if (pinchStart > 0 && abs(pinchLast-pinchStart)>dp(28)) {
                if (pinchLast > pinchStart) { inputTransport.zoomIn(); showInfo("Zoom In") } else { inputTransport.zoomOut(); showInfo("Zoom Out") }
                return
            }
            // Large release motion is treated as a flick; smaller motion is scroll.
            if (abs(dx)>dp(80) || abs(dy)>dp(80)) {
                if (abs(dx)>abs(dy)) {
                    inputTransport.startActiveWindowSelection(previous = dx < 0); inputTransport.commitActiveWindowSelection(); showInfo("Active Window Switch")
                } else {
                    if(dy<0){inputTransport.home(); showInfo("Top")}else{inputTransport.end(); showInfo("Bottom")}
                }
            } else if (abs(dx)>abs(dy) && abs(dx)>dp(18)) {
                inputTransport.horizontalScroll(if(dx<0)-4 else 4); showInfo(if(dx<0)"Horizontal Scroll Left" else "Horizontal Scroll Right")
            } else if (abs(dy)>dp(18)) {
                inputTransport.scroll(if(dy<0)4 else -4); showInfo(if(dy<0)"Scroll Up" else "Scroll Down")
            }
        }
    }

    private fun updateFloatingButtonVisual() {
        val button = floatingButton ?: return
        val fill = when {
            floatingLocked -> Color.rgb(245, 210, 70)
            floatingUsed -> Color.rgb(190, 235, 200)
            else -> Win98Style.FACE
        }
        animateButtonColor(button, fill, Color.rgb(176, 194, 211), 10, Color.rgb(24, 52, 78))
    }

    private fun showHelpOverlay(anchor: View) {
        helpPopup?.dismiss()
        onHelpOpened()
        onHelpActivity()
        helpOverlayVisible = true
        // Operation help is intentionally kept in sync with the fixed v1 operation map.
        // From this version onward the gesture/operation assignments are treated as frozen.
        val pages = listOf(
            "HELP  1/7\n\nBASIC / COLOR\n\nInfo: Hold to open Help\nSwipe Left / Right: Change Help page\nSwipe Up / Down: Scroll Help\nReturn: Close Help\n\nYELLOW: Stop / Lock\nGREEN: Active / Good\nEmerald Green: Modifier LOCK\n\nFloating\nTap: Run Floating\nHold 2 sec: Floating Lock ON / OFF\n\nConfig\nTap: Open Config\nHold: Bluetooth AutoConnect / Disconnect",
            "TouchPad  2/7\n\n1 FINGER\nTap: Left Click\nDouble Tap: Double Click\nDouble Tap + Move: Drag / Move Window\nRelease: End Drag\n\n2 FINGERS\nMove Up / Down: Scroll Up / Down\nMove Left / Right: Horizontal Scroll\nPinch Out / In: Zoom In / Out\nFlick Left / Right: Active Window Switch\nFlick Up / Down: Top / Bottom\nDouble Tap Short: Right Click\nDouble Tap Long: Center Click",
            "Stick A / B  3/7\n\nAll stick gestures must START from the center knob.\nAction fires when the gesture is completed.\n\nSTICK A\nUp: Redo\nDown: Undo\nLeft: Back\nRight: Forward\n\nSTICK B\nUp: Select All\nDown: Delete\nLeft: Copy\nLeft Hold 1 sec or more: Cut\nRight: Paste",
            "A + B  4/7\n\nOperate A and B together.\n\nA Left + B Right: Find\nA Right + B Left: Esc\nA Up + B Down: Show Desktop\nA Down + B Up: Show Desktop / Return\nA + B Down: Refresh (F5)\nA + B Up: Mute\n\nA + B CENTER\nHold both centers 2 sec: Pointer Shake\nThe pointer shakes horizontally and returns near its starting position.",
            "Modifiers  5/7\n\nTOP CONTROLS\nFloating / WIN / Info / Mouse / Config\n\nSHIFT / CTRL / ALT\nTap: Apply to the NEXT operation\nTap again: Cancel\nHold 1 sec: LOCK ON / OFF\nLOCK color: Emerald Green\n\nCLEAR\nRelease all SHIFT / CTRL / ALT states\n\nWIN\nTap: Windows key\n\nMouse\nLandscape: Close Android IME and return to Mouse operation",
            "C Stick / IME  6/7\n\nC CENTER\nWhen input is locked: First touch unlocks keyboard input only.\nLandscape: C or Enter can open/keep the Android IME session.\n\nC DIRECTION\nFlick Up / Down / Left / Right: Arrow key once\nHold direction about 0.4 sec: Continuous Arrow movement\nRelease: Stop continuous movement\n\nTEXT SELECT\nDouble tap C center, keep the 2nd tap held 1 sec\nMove C: Shift + Arrow selection\nMouse pointer does NOT move\nRelease C: Finish operation and keep text highlighted",
            "Keyboard  7/7\n\nHan/Zen\nSend Half-width / Full-width key to Windows\n\nMuhenkan\nRepeated taps cycle:\n1: Full-width Katakana (F7)\n2: Half-width Katakana (F8)\n3: Hiragana / original reading (F6)\n\nHenkan\nSend Space for Windows IME conversion\n\nEnter\nIf input is locked: Unlock input only\nIf input is active: Send Enter\nLandscape: Enter also opens/keeps Android IME\n\nLandscape rule\nAndroid IME stays visible until Mouse is pressed."
        )
        var page = 0
        val box = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(255, 18, 29, 42))
            isClickable = true
        }
        val returnButton = TextView(activity).apply {
            text = "Return"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(Color.rgb(55,75,96), Color.rgb(130,155,180), 10); isClickable = true
            installPressAnimation(this)
            setOnClickListener {
                onHelpActivity()
                helpPopup?.dismiss()
            }
        }
        box.addView(returnButton, FrameLayout.LayoutParams(dp(120), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
        })

        val text = TextView(activity).apply {
            this.text = pages[0]
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(28))
        }

        // Landscape Help needs vertical scrolling because the available height is
        // much shorter than portrait. Keep horizontal page swipes, but let normal
        // vertical gestures be handled by ScrollView all the way to the last line.
        val helpScroll = object : ScrollView(activity) {
            private var downX = 0f
            private var downY = 0f

            override fun onTouchEvent(event: MotionEvent): Boolean {
                onHelpActivity()
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (abs(dx) > dp(60) && abs(dx) > abs(dy)) {
                            page = (page + if (dx < 0) 1 else -1).coerceIn(0, pages.lastIndex)
                            text.animate().alpha(0f).setDuration(90).withEndAction {
                                text.text = pages[page]
                                scrollTo(0, 0)
                                text.animate().alpha(1f).setDuration(120).start()
                            }.start()
                            return true
                        }
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            isFillViewport = true
            addView(text, FrameLayout.LayoutParams(-1, -2))
        }

        box.addView(helpScroll, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(72)
            bottomMargin = dp(8)
        })
        returnButton.bringToFront()
        helpPopup=PopupWindow(box,-1,-1,true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); isTouchable=true; isOutsideTouchable=false; elevation=dp(24).toFloat(); showAtLocation(anchor,Gravity.FILL,0,0)
            box.alpha = 0f
            box.scaleX = 0.96f
            box.scaleY = 0.96f
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start()
            setOnDismissListener {
                helpOverlayVisible = false
                showInfo("Info")
                onHelpClosed()
            }
        }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        shape=GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1).coerceAtLeast(1),stroke); cornerRadius=dp(radiusDp).toFloat()
    }

    private fun showInfo(message: String) {
        if (helpOverlayVisible) return
        val label = message.ifBlank { "Info" }
        infoIdleReset?.let { handler.removeCallbacks(it) }
        listOfNotNull(infoBar, infoBarMirror).forEach { v ->
            if (v.text.toString() != label) {
                v.animate().cancel()
                v.alpha = 0.72f
                v.text = label
                v.animate().alpha(1f).setDuration(140L).start()
            }
        }
        if (label != "Info") {
            infoIdleReset = Runnable {
                if (!helpOverlayVisible && !configMode) {
                    listOfNotNull(infoBar, infoBarMirror).forEach { v ->
                        v.animate().cancel()
                        v.alpha = 0.72f
                        v.text = "Info"
                        v.animate().alpha(1f).setDuration(140L).start()
                    }
                }
            }
            handler.postDelayed(infoIdleReset!!, 30000L)
        } else {
            infoIdleReset = null
        }
    }
    fun showSinglePress(name: String) = showInfo(name)
    fun setConfigMode(active: Boolean) { configMode=active; helpOverlayVisible = false; infoIdleReset?.let { handler.removeCallbacks(it) }; infoIdleReset = null; showInfo(if(active) "Config" else "Info") }

    fun setConnectionVisualState(state: ConnectionVisualState) {
        val previous = lastState
        lastState = state
        if (!floatingTransparencyActive) {
            applyConnectionVisualState(state)
            if (state == ConnectionVisualState.CONNECTED && previous != ConnectionVisualState.CONNECTED) {
                listOfNotNull(infoBar, infoBarMirror).forEach { v ->
                    v.animate().cancel()
                    v.scaleX = 1f; v.scaleY = 1f
                    v.animate().scaleX(1.025f).scaleY(1.025f).setDuration(150L).withEndAction {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(190L).start()
                    }.start()
                }
            }
        }
    }
    private fun applyConnectionVisualState(state: ConnectionVisualState) {
        val infoViews = listOfNotNull(infoBar, infoBarMirror)
        if (infoViews.isEmpty()) return
        blinkAnimator?.cancel(); blinkAnimator = null
        fun applyInfoColor(color: Int) {
            infoViews.forEach { v ->
                // Square, hard-edged Win98 status/title bar. No rounded Material treatment.
                v.background = Win98Style.raisedBackground(color, dp(2))
                v.setTextColor(if (color == Color.WHITE) Win98Style.TEXT else Color.WHITE)
            }
        }
        when (state) {
            ConnectionVisualState.IDLE -> applyInfoColor(Color.WHITE)
            ConnectionVisualState.CONNECTED -> applyInfoColor(INFO_BLUE)
            ConnectionVisualState.ERROR -> applyInfoColor(Color.RED)
            ConnectionVisualState.CONNECTING -> {
                applyInfoColor(Color.WHITE)
                blinkAnimator = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, INFO_BLUE).apply {
                    duration = 700L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { if (!floatingTransparencyActive) applyInfoColor(it.animatedValue as Int) }
                    start()
                }
            }
        }
    }
    fun setFloatingTransparency(active:Boolean){ floatingTransparencyActive=active; if(active){blinkAnimator?.cancel();surface?.setBackgroundColor(Color.TRANSPARENT)}else { surface?.setBackgroundColor(Win98Style.FACE); applyConnectionVisualState(lastState) } }
    fun restoreSavedBackgroundOnResume(){ if(!floatingTransparencyActive) { surface?.setBackgroundColor(Win98Style.FACE); applyConnectionVisualState(lastState) } }
    fun suspendSavedBackgroundForFloating(){ blinkAnimator?.cancel() }

    companion object { private val INFO_BLUE=Win98Style.TITLE_BLUE_ACTIVE }
}
