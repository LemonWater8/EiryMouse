package com.example.diazymouse.ui.screen

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.connection.BluetoothHidBridge
import com.example.diazymouse.floating.FloatingOverlayService
import com.example.diazymouse.ui.Win98Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * EiryMouse text-entry surface.
 *
 * Startup contract:
 * - Android IME is shown immediately.
 * - The visible TextArea is NOT focused/selected at startup.
 * - A tiny transparent proxy editor owns the initial IME focus.
 * - The visible TextArea becomes editable only after the user taps it.
 */
class KeyboardUiController(
    private val activity: ComponentActivity,
    private val bluetoothHid: BluetoothHidBridge,
    private val landscapeMode: Boolean = false,
    private val onSinglePress: (String) -> Unit = {},
    private val onTextDragStart: () -> Unit = {},
    private val onTextDragMove: (Int, Int) -> Unit = { _, _ -> },
    private val onTextDragEnd: () -> Unit = {}
) {
    companion object {
        fun isLandscapeCGuardEnvelope(
            centerX: Float,
            centerY: Float,
            rawX: Float,
            rawY: Float,
            cNotchRadius: Float,
            guardWidth: Float,
            guardHeight: Float = 122f,
            rightExtraCoverage: Float = 0f
        ): Boolean {
            val leftGuardRect = android.graphics.RectF(
                centerX - cNotchRadius - guardWidth,
                centerY - guardHeight / 2f,
                centerX - cNotchRadius,
                centerY + guardHeight / 2f
            )
            val rightGuardRect = android.graphics.RectF(
                centerX + cNotchRadius,
                centerY - guardHeight / 2f,
                centerX + cNotchRadius + guardWidth + rightExtraCoverage,
                centerY + guardHeight / 2f
            )
            return leftGuardRect.contains(rawX, rawY) || rightGuardRect.contains(rawX, rawY)
        }
    }

    private enum class LandscapeInputState { MOUSE, KEYBOARD }

    private var landscapeInputState: LandscapeInputState = LandscapeInputState.MOUSE
    private var imeProxy: EditText? = null
    private var hankakuZenkakuButton: Button? = null
    private var convertButton: Button? = null
    private var henkanButton: Button? = null
    private var muhenkanButton: Button? = null
    private var directionLever: DirectionLeverView? = null
    private var landscapeCGuardLeft: View? = null
    private var landscapeCGuardRight: View? = null
    private var rootView: FrameLayout? = null
    private var inputBlockerPopup: PopupWindow? = null
    private var blockerClockView: TextView? = null
    private val blockerClockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val blockerClockRunnable = object : Runnable {
        override fun run() {
            val formatter = SimpleDateFormat("yyyy/MM/dd\nHH:mm:ss", Locale.JAPAN).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            }
            blockerClockView?.text = formatter.format(Date())
            if (inputBlockerPopup?.isShowing == true) blockerClockHandler.postDelayed(this, 1_000L)
        }
    }
    private var lastImeHeightPx: Int = 0
    private var inputControlsEnabled: Boolean = true
    private var inputSessionActive: Boolean = false
    // True while the full-screen Help surface is visible.  This gate must be
    // checked by every IME-show path because showSoftInput() is posted and can
    // otherwise race with the Help transition.
    private var helpMode: Boolean = false
    // Config/Help/orientation-transition surfaces must never request or lock the IME.
    private var nonInputSurfaceActive: Boolean = false
    private var lastComposingHidText: String = ""
    private var lastStableGodanSource: String = ""
    private var compositionMirroredSinceLastCommit: Boolean = false
    private var muhenkanCycleStep: Int = 0
    private var suppressNextImeDelEvents: Int = 0
    private var suppressImeDelUntilMs: Long = 0L

    fun attach(root: FrameLayout) {
        rootView = root
        ensureImeProxy(root)
        ensureInputActionRow(root)

        bluetoothHid.setKeyboardEnabled(true)

        // Portrait starts with text input LOCKED. Landscape has no keyboard-lock
        // state: the IME is simply hidden until Enter / C-stick requests it.
        inputSessionActive = landscapeMode
        updateDirectionLeverState()
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                (if (landscapeMode)
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                else
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            // Primary source: use the IME height reported by Android itself.
            // The value can change when the user switches IME, changes keyboard
            // height, toggles the candidate/tool row, or the window size changes.
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeHeight > 0) {
                lastImeHeightPx = imeHeight
            }

            // Android 15+ edge-to-edge behavior and some vendor builds may leave
            // adjustResize effectively behaving like an overlay: the root keeps its
            // full height while the IME covers the lower controls. Detect that case
            // from the real visible window frame and reserve ONLY the overlapped IME
            // height. On devices where adjustResize already resized the root, overlap
            // stays small and no extra padding is added, avoiding a double inset.
            applyImeSafeBottomInset(root, imeHeight)

            if (landscapeMode) {
                dismissInputBlocker()
            } else if (isInputSessionActive()) {
                dismissInputBlocker()
            } else {
                showInputBlocker()
            }
            insets
        }

        if (landscapeMode) {
            forceLandscapeMouseMode()
        } else {
            root.post {
                focusProxyAndShowKeyboard(showBlockerAfter = true)
            }
        }
    }


    /**
     * Keep EiryMouse bottom controls above the Android IME on both classic
     * adjustResize devices and newer edge-to-edge/vendor implementations.
     *
     * We intentionally do not blindly apply imeHeight as padding: older devices
     * that already resize the Activity would then receive the IME inset twice.
     * Instead, compare the root's real screen bottom with Android's visible frame.
     * Only when the root is substantially covered by the IME do we reserve space.
     */
    private fun applyImeSafeBottomInset(root: View, imeHeight: Int) {
        val visibleFrame = Rect()
        root.getWindowVisibleDisplayFrame(visibleFrame)

        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val rootBottomOnScreen = location[1] + root.height
        val coveredPx = (rootBottomOnScreen - visibleFrame.bottom).coerceAtLeast(0)

        // Navigation bars can create a small coveredPx even when adjustResize is
        // already working. Require at least half of the reported IME height before
        // treating the keyboard as an overlay.
        val safeBottom = if (imeHeight > 0 && coveredPx >= imeHeight / 2) {
            minOf(imeHeight, coveredPx)
        } else {
            0
        }

        if (root.paddingBottom != safeBottom) {
            root.setPadding(
                root.paddingLeft,
                root.paddingTop,
                root.paddingRight,
                safeBottom
            )
            root.requestLayout()
            Log.i(
                "EIRY_IME",
                "IME safe-area applied: ime=$imeHeight covered=$coveredPx bottomPadding=$safeBottom " +
                    "root=${root.width}x${root.height}"
            )
        }
    }

    fun toggleKeyboardWithHanZen(): Boolean {
        bluetoothHid.setKeyboardEnabled(true)
        // Landscape rule: only C-stick and Enter may OPEN the Android IME.
        // Han/Zen may still send its HID key, but must not make Gboard appear.
        if (!landscapeMode) {
            activateInputSession()
        }
        return true
    }

    fun ensureKeyboardVisible() {
        if (helpMode || nonInputSurfaceActive) return
        bluetoothHid.setKeyboardEnabled(true)
        activateInputSession()
    }

    /**
     * Mouse action.  Landscape must fully detach the hidden IME proxy from
     * input focus so Gboard cannot remain the touch target after rotation.
     */
    fun hideKeyboardForOperation() {
        if (helpMode || nonInputSurfaceActive) return
        if (landscapeMode) {
            forceLandscapeMouseMode()
        } else {
            // Portrait keeps the established lock/session semantics.
            suspendInputSession()
        }
    }

    /**
     * Force the Landscape screen into mouse-operation state.  This is stronger
     * than only hiding the IME: clear the proxy focus, make it non-focusable for
     * the current mouse surface, cancel pending input state, and issue an IME hide.
     * Enter/C can make the proxy focusable again via activateInputSession().
     */
    fun forceLandscapeMouseMode() {
        Log.w(
            "EIRY_TOUCH",
            "forceLandscapeMouseMode CALLED\n" +
                Log.getStackTraceString(Throwable("forceLandscapeMouseMode caller"))
        )
        enterLandscapeMouseMode()
    }

    /**
     * Landscape has exactly two input states. MOUSE owns the app surface and
     * keeps the hidden IME proxy detached. KEYBOARD is entered only by an
     * explicit keyboard request from Enter or the C-stick only.
     */
    private fun enterLandscapeMouseMode() {
        if (!landscapeMode) return
        landscapeInputState = LandscapeInputState.MOUSE
        inputSessionActive = false
        dismissInputBlocker()
        imeProxy?.apply {
            clearFocus()
            isFocusable = false
            isFocusableInTouchMode = false
        }
        activity.currentFocus?.takeIf { it === imeProxy }?.clearFocus()
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        hideSoftKeyboard()
        rootView?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        Log.i("EIRY_TOUCH", "LandscapeInputState=MOUSE IME detached")
        updateDirectionLeverState()
    }

    private fun enterLandscapeKeyboardMode() {
        if (!landscapeMode || !inputControlsEnabled || helpMode || nonInputSurfaceActive) return
        landscapeInputState = LandscapeInputState.KEYBOARD
        inputSessionActive = true
        imeProxy?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        dismissInputBlocker()
        Log.i("EIRY_TOUCH", "LandscapeInputState=KEYBOARD IME enabled")
        updateDirectionLeverState()
        focusProxyAndShowKeyboard(restartImeConnection = true)
    }

    /**
     * App-side touch gate. Han/Zen, Muhenkan and Convert keep the current
     * keyboard session alive. Any other app control suspends text input and
     * places the dark blocker over the Android IME. Touches inside the IME are
     * outside this root view and therefore naturally keep input active.
     */
    fun handleAppTouch(rawX: Float, rawY: Float) {
        // Landscape rule (2026-09-13): once the Android IME is visible, ordinary
        // app touches are never allowed to dismiss it.  The explicit Mouse button
        // is the sole runtime path that may call forceLandscapeMouseMode().
        if (landscapeMode) return

        if (isTouchInside(hankakuZenkakuButton, rawX, rawY) ||
            isTouchInside(muhenkanButton, rawX, rawY) ||
            isTouchInside(directionLever, rawX, rawY) ||
            isTouchInside(henkanButton, rawX, rawY) ||
            isTouchInside(convertButton, rawX, rawY)
        ) {
            return
        }
        suspendInputSession()
    }

    private fun isTouchInside(view: View?, rawX: Float, rawY: Float): Boolean {
        val target = view ?: return false
        if (target.visibility != View.VISIBLE || !target.isShown) return false
        val rect = android.graphics.Rect()
        target.getGlobalVisibleRect(rect)
        return rect.contains(rawX.toInt(), rawY.toInt())
    }

    /**
     * Landscape-only inert strips beside the C lever. When the Android keyboard
     * is visible, these black frames are treated as a dead-zone so they do not
     * dismiss or interrupt the active keyboard session. If the keyboard is not
     * visible, they are no longer treated as an IME-preservation zone.
     */
    fun isLandscapeCDeadZoneTouch(rawX: Float, rawY: Float): Boolean {
        // The standalone black C guards no longer exist; their area has been
        // merged into Info. Landscape IME state is now preserved globally and
        // may only be dismissed by the explicit Mouse button action.
        return false
    }

    private fun dpToPx(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    /**
     * A tap on the Landscape C guard is deliberately inert.  If the user is
     * already typing, preserve that exact keyboard session: do not enter mouse
     * mode, do not restart the InputConnection, and do not hide/re-show the IME.
     * Reassert focus only if Android has momentarily moved it away from the
     * hidden proxy.
     */
    fun preserveLandscapeKeyboardOnCGuardTap() {
        if (!landscapeMode || !isAndroidKeyboardVisible()) return

        // Guard taps are inert.  Do not restart the InputConnection, switch
        // modes, or call hide/show IME.  Keep the current keyboard state intact.
        imeProxy?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            if (!hasFocus()) requestFocus()
        }
        dismissInputBlocker()
        updateDirectionLeverState()
        Log.v("EIRY_TOUCH", "Landscape C guard tap: IME visible; preserve all keyboard state")
    }

    private fun isAndroidKeyboardVisible(): Boolean {
        val root = rootView ?: return false
        val insets = ViewCompat.getRootWindowInsets(root) ?: return false
        val ime = WindowInsetsCompat.Type.ime()
        // Some Samsung/Android builds briefly report isVisible=false while the
        // IME inset is still present.  Treat either signal as visible.
        return insets.isVisible(ime) || insets.getInsets(ime).bottom > 0
    }

    fun lockInputSession() {
        // Landscape never enters the keyboard-lock state. Closing the IME is
        // allowed, but the input session itself remains ready/unlocked.
        suspendInputSession()
    }

    fun enterHelpMode() {
        helpMode = true
        suppressImeAndLockForNonInputSurface()
    }

    fun exitHelpMode() {
        helpMode = false
        // If Config is still active, setInputControlsEnabled(false) keeps this gate closed.
        if (!inputControlsEnabled) return
        nonInputSurfaceActive = false
        if (!landscapeMode) {
            focusProxyAndShowKeyboard(showBlockerAfter = true)
        } else {
            // Landscape: do not change IME visibility here.  If it was visible
            // before Help, it stays visible; if hidden, it stays hidden.
            updateDirectionLeverState()
            dismissInputBlocker()
        }
    }

    /**
     * Called immediately before changing screen orientation.  This is intentionally
     * unconditional: even when Android reports the IME as hidden, cancel any pending
     * show request, remove keyboard lock, clear focus, and issue one hide request.
     */
    fun prepareForOrientationChange() {
        inputControlsEnabled = false
        suppressImeAndLockForNonInputSurface()
        activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }

    private fun suppressImeAndLockForNonInputSurface() {
        nonInputSurfaceActive = true
        if (landscapeMode) {
            // Landscape rule: Config/Help and other app surfaces are not allowed
            // to dismiss an IME that is already visible. Mouse is the only close trigger.
            dismissInputBlocker()
            updateDirectionLeverState()
            return
        }
        inputSessionActive = false
        updateDirectionLeverState()
        dismissInputBlocker()
        imeProxy?.clearFocus()
        hideSoftKeyboard()
    }

    fun setInputControlsEnabled(enabled: Boolean) {
        inputControlsEnabled = enabled
        val visibility = if (enabled) View.VISIBLE else View.GONE
        convertButton?.visibility = visibility
        henkanButton?.visibility = visibility
        muhenkanButton?.visibility = visibility
        directionLever?.visibility = visibility
        hankakuZenkakuButton?.visibility = visibility

        // Config/Help are true non-input surfaces in both orientations.
        // Never show the IME and never fire the keyboard-lock overlay here.
        if (!enabled) {
            suppressImeAndLockForNonInputSurface()
        } else {
            nonInputSurfaceActive = false
            if (landscapeMode) {
                // Preserve current IME visibility.  Do not implicitly enter Mouse mode.
                updateDirectionLeverState()
                dismissInputBlocker()
            } else if (!helpMode) {
                focusProxyAndShowKeyboard(showBlockerAfter = true)
            }
        }
    }

    fun release() {
        dismissInputBlocker()
        hideSoftKeyboard()
        imeProxy = null
        hankakuZenkakuButton = null
        convertButton = null
        henkanButton = null
        muhenkanButton = null
        directionLever = null
        landscapeCGuardLeft = null
        landscapeCGuardRight = null
        rootView = null
        inputSessionActive = false
        landscapeInputState = LandscapeInputState.MOUSE
        lastComposingHidText = ""
        lastStableGodanSource = ""
        compositionMirroredSinceLastCommit = false
        muhenkanCycleStep = 0
        suppressNextImeDelEvents = 0
        suppressImeDelUntilMs = 0L
    }

    private fun observeImeInputConnection(
        targetName: String,
        base: InputConnection?
    ): InputConnection? {
        if (base == null) return null

        return object : InputConnectionWrapper(base, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                notifyFloatingImeActivity()
                Log.v("EIRY_IME", "$targetName commitText: $text")

                if (targetName == "ImeProxy") {
                    val mirroredBeforeCommit = if (compositionMirroredSinceLastCommit) lastComposingHidText else ""
                    forwardCommittedTextToBluetoothHid(
                        text = text,
                        mirroredPrefix = mirroredBeforeCommit
                    )
                    // Candidate selection/commit on Android happens before the Enter
                    // KeyEvent.  Do not synthesize an extra Enter here: one Android Enter
                    // must map to exactly one Windows HID Enter, otherwise the second Enter
                    // becomes an unwanted newline after Windows IME has already confirmed.
                    lastComposingHidText = ""
                    lastStableGodanSource = ""
                    compositionMirroredSinceLastCommit = false
                }

                return super.commitText(text, newCursorPosition)
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                notifyFloatingImeActivity()
                Log.v("EIRY_IME", "$targetName setComposingText: $text")
                if (targetName == "ImeProxy") {
                    forwardComposingTextToBluetoothHid(text)
                }
                return super.setComposingText(text, newCursorPosition)
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                notifyFloatingImeActivity()
                Log.v("EIRY_IME", "$targetName performEditorAction: action=$editorAction")
                if (targetName == "ImeProxy") {
                    // Treat the Android IME action key as "confirm" for the PC side.
                    // Consume it locally so the invisible proxy never inserts a newline.
                    forwardImeKeyEventToBluetoothHid(KeyEvent.KEYCODE_ENTER)
                    return true
                }
                return super.performEditorAction(editorAction)
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                notifyFloatingImeActivity()
                Log.v("EIRY_IME", "$targetName sendKeyEvent: $event")

                if (targetName == "ImeProxy" && event != null && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        // Gboard has already committed the selected Android candidate by
                        // this point. Send one HID Enter to confirm Windows IME, then consume
                        // the Android event so it cannot become a newline in the proxy.
                        forwardImeKeyEventToBluetoothHid(event.keyCode)
                    }
                    return true
                }

                if (targetName == "ImeProxy" && event?.action == KeyEvent.ACTION_DOWN) {
                    forwardImeKeyEventToBluetoothHid(event.keyCode)
                }

                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                notifyFloatingImeActivity()
                Log.v(
                    "EIRY_IME",
                    "$targetName deleteSurroundingText: before=$beforeLength after=$afterLength"
                )
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    private fun notifyFloatingImeActivity() {
        val intent = Intent(activity, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_INTERACTION_PULSE
        }
        runCatching { activity.startService(intent) }
    }

    private fun forwardImeKeyEventToBluetoothHid(keyCode: Int) {
        if (!isInputSessionActive()) {
            Log.v("EIRY_HID", "skip keyEvent input suspended: keyCode=$keyCode")
            return
        }
        // A composing shrink is mirrored immediately. Gboard can then emit a
        // delayed KEYCODE_DEL for the same user action. Suppress only inside a
        // short correlation window so stale counters cannot swallow later,
        // unrelated Backspace presses.
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            val now = android.os.SystemClock.uptimeMillis()
            if (suppressNextImeDelEvents > 0 && now <= suppressImeDelUntilMs) {
                suppressNextImeDelEvents--
                if (suppressNextImeDelEvents == 0) suppressImeDelUntilMs = 0L
                Log.v(
                    "EIRY_HID",
                    "suppress correlated DEL already mirrored; remaining=$suppressNextImeDelEvents"
                )
                return
            }
            if (suppressNextImeDelEvents > 0) {
                Log.v("EIRY_HID", "expire stale DEL suppression: pending=$suppressNextImeDelEvents")
                suppressNextImeDelEvents = 0
                suppressImeDelUntilMs = 0L
            }
        }
        val hidKey = when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> HidKeyboardPacket.KEY_SPACE
            KeyEvent.KEYCODE_ENTER -> HidKeyboardPacket.KEY_ENTER
            KeyEvent.KEYCODE_DEL -> HidKeyboardPacket.KEY_BACKSPACE
            KeyEvent.KEYCODE_DPAD_LEFT -> HidKeyboardPacket.KEY_LEFT_ARROW
            KeyEvent.KEYCODE_DPAD_RIGHT -> HidKeyboardPacket.KEY_RIGHT_ARROW
            KeyEvent.KEYCODE_DPAD_UP -> HidKeyboardPacket.KEY_UP_ARROW
            KeyEvent.KEYCODE_DPAD_DOWN -> HidKeyboardPacket.KEY_DOWN_ARROW
            KeyEvent.KEYCODE_TAB -> HidKeyboardPacket.KEY_TAB
            else -> null
        }

        if (hidKey == null) {
            Log.v("EIRY_HID", "skip unsupported keyEvent: keyCode=$keyCode")
            return
        }
        if (!bluetoothHid.isKeyboardEnabled()) {
            Log.v("EIRY_HID", "skip keyEvent keyboard disabled: keyCode=$keyCode")
            return
        }
        if (!bluetoothHid.isConnected()) {
            Log.v("EIRY_HID", "skip keyEvent Bluetooth not connected: keyCode=$keyCode")
            return
        }

        val sent = bluetoothHid.pressKey(hidKey)
        Log.v(
            "EIRY_HID",
            "sendKeyEvent -> HID: keyCode=$keyCode hidKey=${hidKey.toInt() and 0xFF} sent=$sent"
        )
    }

    private fun isGodanIntermediateLatin(ch: Char): Boolean =
        ch in '\uFF21'..'\uFF3A' || ch in '\uFF41'..'\uFF5A'

    private fun stableGodanSource(source: String): String =
        source.filterNot(::isGodanIntermediateLatin)

    private fun forwardComposingTextToBluetoothHid(text: CharSequence?) {
        val source = text?.toString().orEmpty()
        if (!isInputSessionActive()) {
            Log.v("EIRY_HID", "skip composing input suspended: source=$source")
            return
        }

        // Godan temporarily mixes full-width latin letters into composing text
        // (e.g. へｔんｎ).  Do not discard the whole composing string.  Remove
        // only those transient latin letters and rebuild the HID mirror from the
        // kana that is already stable.  This lets "ん" advance independently
        // even while the next Godan stroke is still unresolved.
        val stableSource = stableGodanSource(source)
        if (stableSource != source) {
            Log.v(
                "EIRY_HID",
                "Godan stable projection: source=$source stable=$stableSource previous=$lastComposingHidText"
            )
        }

        // Fast path for long Godan compositions: when stable kana only grew,
        // convert just the newly confirmed suffix and append it to the already
        // mirrored HID text.  Pair/small-tsu cases that cannot be safely
        // converted as a suffix fall back to the full deterministic converter.
        val incrementalHid = if (
            stableSource.startsWith(lastStableGodanSource) &&
            stableSource.length > lastStableGodanSource.length
        ) {
            val addedStable = stableSource.substring(lastStableGodanSource.length)
            val convertedAddition = when {
                addedStable.isEmpty() -> ""
                HidKeyboardPacket.isSupportedDirectInput(addedStable) -> addedStable
                else -> KanaRomajiConverter.convert(addedStable)
            }
            convertedAddition
                ?.takeIf { HidKeyboardPacket.isSupportedDirectInput(it) }
                ?.let { lastComposingHidText + it }
        } else null

        val hidText = incrementalHid ?: when {
            stableSource.isEmpty() -> ""
            HidKeyboardPacket.isSupportedDirectInput(stableSource) -> stableSource
            else -> KanaRomajiConverter.convert(stableSource)
        }

        if (hidText == null || !HidKeyboardPacket.isSupportedDirectInput(hidText)) {
            Log.v("EIRY_HID", "skip unsupported composingText: source=$source stable=$stableSource")
            return
        }
        if (!bluetoothHid.isKeyboardEnabled()) {
            Log.v("EIRY_HID", "skip composing keyboard disabled: source=$source hid=$hidText")
            return
        }
        if (!bluetoothHid.isConnected()) {
            Log.v("EIRY_HID", "skip composing Bluetooth not connected: source=$source hid=$hidText")
            return
        }

        val previous = lastComposingHidText

        when {
            hidText == previous -> {
                Log.v("EIRY_HID", "composing unchanged: source=$source hid=$hidText")
            }

            hidText.startsWith(previous) -> {
                val addition = hidText.substring(previous.length)
                val sent = addition.isEmpty() || bluetoothHid.typeAsciiText(addition)
                if (sent) {
                    lastComposingHidText = hidText
                    if (addition.isNotEmpty()) compositionMirroredSinceLastCommit = true
                }
                Log.v(
                    "EIRY_HID",
                    "composing append -> HID: source=$source previous=$previous add=$addition current=$hidText sent=$sent"
                )
            }

            previous.startsWith(hidText) -> {
                // Windows IME has already converted the romaji stream into a
                // composing Japanese string. Therefore one Android kana deletion
                // must be mirrored as one Windows Backspace, even when that kana
                // originally required multiple roman keys (e.g. ん -> nn).
                val previousStable = lastStableGodanSource
                val sourceDeleteCount = when {
                    previousStable.startsWith(stableSource) ->
                        (previousStable.codePointCount(0, previousStable.length) -
                            stableSource.codePointCount(0, stableSource.length)).coerceAtLeast(1)
                    else -> 1
                }
                var sentCount = 0
                repeat(sourceDeleteCount) {
                    if (bluetoothHid.pressKey(HidKeyboardPacket.KEY_BACKSPACE)) sentCount++
                }
                if (sentCount == sourceDeleteCount) {
                    lastComposingHidText = hidText
                    suppressNextImeDelEvents += sourceDeleteCount
                    suppressImeDelUntilMs = android.os.SystemClock.uptimeMillis() + 450L
                }
                Log.v(
                    "EIRY_HID",
                    "composing shrink -> HID backspace: source=$source previousSource=$previousStable currentSource=$stableSource previousHid=$previous currentHid=$hidText delete=$sourceDeleteCount sent=$sentCount suppressPending=$suppressNextImeDelEvents"
                )
            }

            else -> {
                // Godan's small/large key rewrites an already composed kana in
                // place (e.g. い -> ぃ, つ -> っ).  Mirroring the new full
                // romaji string would append duplicate text on Windows.
                // Instead, find the unchanged kana prefix, erase only the
                // rewritten Android kana from the Windows IME composition, and
                // type the romaji for the replacement suffix.
                val previousStable = lastStableGodanSource
                var commonPrefixLength = 0
                val maxCommon = minOf(previousStable.length, stableSource.length)
                while (commonPrefixLength < maxCommon &&
                    previousStable[commonPrefixLength] == stableSource[commonPrefixLength]) {
                    commonPrefixLength++
                }

                val oldSuffix = previousStable.substring(commonPrefixLength)
                val newSuffix = stableSource.substring(commonPrefixLength)
                val replacementHid = when {
                    newSuffix.isEmpty() -> ""
                    HidKeyboardPacket.isSupportedDirectInput(newSuffix) -> newSuffix
                    else -> KanaRomajiConverter.convert(newSuffix)
                }

                val sourceDeleteCount = oldSuffix.codePointCount(0, oldSuffix.length)
                var deleted = 0
                if (replacementHid != null && HidKeyboardPacket.isSupportedDirectInput(replacementHid)) {
                    repeat(sourceDeleteCount) {
                        if (bluetoothHid.pressKey(HidKeyboardPacket.KEY_BACKSPACE)) deleted++
                    }
                    val typed = deleted == sourceDeleteCount &&
                        (replacementHid.isEmpty() || bluetoothHid.typeAsciiText(replacementHid))
                    if (typed) {
                        lastComposingHidText = hidText
                        if (sourceDeleteCount > 0) {
                            suppressNextImeDelEvents += sourceDeleteCount
                            suppressImeDelUntilMs = android.os.SystemClock.uptimeMillis() + 450L
                        }
                        if (replacementHid.isNotEmpty()) compositionMirroredSinceLastCommit = true
                    }
                    Log.v(
                        "EIRY_HID",
                        "composing rewrite -> replace suffix: source=$source previousSource=$previousStable currentSource=$stableSource oldSuffix=$oldSuffix newSuffix=$newSuffix delete=$sourceDeleteCount replacement=$replacementHid deleted=$deleted typed=$typed"
                    )
                } else {
                    Log.v(
                        "EIRY_HID",
                        "skip unsupported composing rewrite: source=$source previousSource=$previousStable currentSource=$stableSource newSuffix=$newSuffix"
                    )
                }
            }
        }

        // Cache only a successfully representable stable source.  This keeps
        // long-input work proportional to the newly confirmed tail in the
        // common case, while unsafe rewrites still use the full converter.
        lastStableGodanSource = stableSource
    }

    private fun forwardCommittedTextToBluetoothHid(
        text: CharSequence?,
        mirroredPrefix: String = ""
    ) {
        val committed = text?.toString().orEmpty()
        if (!isInputSessionActive()) {
            Log.v("EIRY_HID", "skip commit input suspended: source=$committed")
            return
        }
        if (committed.isEmpty()) return

        val hidText = when {
            HidKeyboardPacket.isSupportedDirectInput(committed) -> committed
            else -> KanaRomajiConverter.convert(committed)
        }

        if (hidText == null || hidText.isEmpty() || !HidKeyboardPacket.isSupportedDirectInput(hidText)) {
            Log.v("EIRY_HID", "skip unsupported commitText: $committed")
            return
        }
        if (!bluetoothHid.isKeyboardEnabled()) {
            Log.v("EIRY_HID", "skip keyboard disabled: source=$committed hid=$hidText")
            return
        }
        if (!bluetoothHid.isConnected()) {
            Log.v("EIRY_HID", "skip Bluetooth not connected: source=$committed hid=$hidText")
            return
        }

        // A final commit can contain more stable kana than the last composing
        // callback.  Send only that suffix instead of suppressing the whole
        // commit.  This is especially important around Godan "ん" sequences.
        val sendText = when {
            mirroredPrefix.isEmpty() -> hidText
            hidText == mirroredPrefix -> ""
            hidText.startsWith(mirroredPrefix) -> hidText.substring(mirroredPrefix.length)
            else -> {
                Log.v(
                    "EIRY_HID",
                    "commit differs from mirrored composing; suppress duplicate-prone rewrite: source=$committed mirrored=$mirroredPrefix current=$hidText"
                )
                ""
            }
        }

        val sent = sendText.isEmpty() || bluetoothHid.typeAsciiText(sendText)
        Log.v(
            "EIRY_HID",
            "commitText -> HID suffix: source=$committed mirrored=$mirroredPrefix current=$hidText add=$sendText sent=$sent"
        )
    }


    private fun ensureImeProxy(root: FrameLayout) {
        val proxy = object : EditText(activity) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                return observeImeInputConnection(
                    "ImeProxy",
                    super.onCreateInputConnection(outAttrs)
                )
            }

            override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    // Do not consume the system Back key here. The hidden proxy is just a
                    // keyboard bridge; swallowing BACK disables normal activity/back-button
                    // navigation and settings escape paths.
                    post { focusProxyAndShowKeyboard() }
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            // Must remain VISIBLE and focusable for Android IME, but is visually absent.
            alpha = 0f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            setHintTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        root.addView(
            proxy,
            FrameLayout.LayoutParams(1, 1).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = 0
                topMargin = 0
            }
        )
        imeProxy = proxy
    }

    private fun ensureInputActionRow(root: FrameLayout) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        fun installPressAnimation(view: View) {
            fun scaleSet(target: Float, durationMs: Long) = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.SCALE_X, target),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, target)
                )
                duration = durationMs
            }
            view.stateListAnimator = StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_pressed), scaleSet(0.96f, 85L))
                addState(intArrayOf(), scaleSet(1f, 125L))
            }
        }

        fun baseButton(label: String, textSizeSp: Float = 11f): Button =
            Button(activity).apply {
                text = label
                textSize = textSizeSp
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
                visibility = View.VISIBLE

                // Unified EiryMouse keyboard-button skin. The four IME controls
                // deliberately share one bright slate visual language; only labels differ.
                // Pressing changes the surface itself to cyan-blue in addition to the
                // existing scale animation, so the action is visible even at a glance.
                setTextColor(Win98Style.TEXT)
                background = Win98Style.buttonBackground(dp(2))
                elevation = 0f
                letterSpacing = 0f
                stateListAnimator = null
            }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val hanZen = baseButton("Han/Zen", 11f).apply {
            setOnClickListener {
                if (bluetoothHid.isKeyboardEnabled() && bluetoothHid.isConnected()) {
                    bluetoothHid.pressKey(HidKeyboardPacket.KEY_HANKAKU_ZENKAKU)
                }
                muhenkanCycleStep = 0
                if (!landscapeMode) activateInputSession()
            }
        }

        val muhenkan = baseButton("Muhenkan", 9f).apply {
            setOnClickListener {
                if (bluetoothHid.isKeyboardEnabled() && bluetoothHid.isConnected()) {
                    // Requested three-step conversion cycle while Windows IME
                    // candidates/composition are active:
                    // 1: full-width Katakana (F7)
                    // 2: half-width Katakana (F8)
                    // 3: return to Hiragana/original reading (F6)
                    val key = when (muhenkanCycleStep) {
                        0 -> HidKeyboardPacket.KEY_F7
                        1 -> HidKeyboardPacket.KEY_F8
                        else -> HidKeyboardPacket.KEY_F6
                    }
                    bluetoothHid.pressKey(key)
                    muhenkanCycleStep = (muhenkanCycleStep + 1) % 3
                    Log.v("EIRY_HID", "Muhenkan cycle -> HID key=${key.toInt() and 0xFF} nextStep=$muhenkanCycleStep")
                }
                onSinglePress("Muhenkan")
                if (!landscapeMode) activateInputSession()
            }
        }

        val lever = DirectionLeverView(activity).apply {
            onUnlockRequested = {
                activateInputSession()
                onSinglePress("Input Unlock")
            }
            // C center press keeps the keyboard session active.
            // Double-tap C and keep the second tap held for 1 second to start Text Select.
            // While held, C movement sends Shift+Arrow so only the text caret/selection moves.
            // Releasing C ends the gesture while the Windows text highlight remains.
            onCenter = { activateInputSession() }
            onTextDragStart = {
                activateInputSession()
                onTextDragStart()
                onSinglePress("Text Select ON")
            }
            onTextDragMove = { dx, dy -> onTextDragMove(dx, dy) }
            onTextDragEnd = {
                onTextDragEnd()
                onSinglePress("Text Selected")
            }
            onDirection = { direction ->
                if (isInputSessionActive()) {
                    val key = when (direction) {
                        LeverDirection.UP -> HidKeyboardPacket.KEY_UP_ARROW
                        LeverDirection.DOWN -> HidKeyboardPacket.KEY_DOWN_ARROW
                        LeverDirection.LEFT -> HidKeyboardPacket.KEY_LEFT_ARROW
                        LeverDirection.RIGHT -> HidKeyboardPacket.KEY_RIGHT_ARROW
                    }
                    if (bluetoothHid.isKeyboardEnabled() && bluetoothHid.isConnected()) {
                        bluetoothHid.pressKey(key)
                    }
                    onSinglePress("Arrow ${direction.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    // Landscape: C direction is an HID Arrow operation only.
                    // Do not reactivate Android IME when the stick is slightly off-center.
                    if (!landscapeMode) {
                        activateInputSession()
                    }
                }
            }
            isInputUnlocked = { isInputSessionActive() }
        }

        val henkan = baseButton("Henkan", 10f).apply {
            setOnClickListener {
                val wasInputActive = isInputSessionActive()
                if (wasInputActive && bluetoothHid.isKeyboardEnabled() && bluetoothHid.isConnected()) {
                    // Windows IME conversion: same HID action as the Space key.
                    bluetoothHid.pressKey(HidKeyboardPacket.KEY_SPACE)
                }
                muhenkanCycleStep = 0
                onSinglePress(if (wasInputActive) "Henkan" else "Henkan")
                if (!landscapeMode) activateInputSession()
            }
        }

        val convert = baseButton("Enter", 10f).apply {
            setOnClickListener {
                // While keyboard input is LOCKED, Enter only unlocks the session.
                // It must not send an HID Enter/newline on that first press.
                val wasInputActive = isInputSessionActive()
                if (wasInputActive && bluetoothHid.isKeyboardEnabled() && bluetoothHid.isConnected()) {
                    bluetoothHid.pressKey(HidKeyboardPacket.KEY_ENTER)
                }
                muhenkanCycleStep = 0
                onSinglePress(if (wasInputActive) "Enter" else "Input Unlock")
                activateInputSession()
            }
        }

        // Keep the lever itself centered on the screen.  The left group and
        // right Convert slot use equal total width around that fixed center.
        // Muhenkan and Enter are intentionally compact. Equal total width on
        // both sides keeps the C lever exactly on the screen centerline.
        // Left controls are equally compact and stay packed toward the left.
        row.addView(hanZen, LinearLayout.LayoutParams(0, dp(46), 0.75f))
        // Keep both button sizes unchanged while opening a little visual gap.
        // The center spacer is reduced by the same amount so C stays fixed.
        row.addView(View(activity), LinearLayout.LayoutParams(dp(14), dp(46)))
        row.addView(muhenkan, LinearLayout.LayoutParams(0, dp(46), 0.75f))
        row.addView(View(activity), LinearLayout.LayoutParams(dp(82), dp(56)))
        // Preserve the former Enter slot, but split it exactly in half.
        // Henkan is on the left, Enter on the right, with a small visual gap.
        row.addView(View(activity), LinearLayout.LayoutParams(0, dp(46), 0.75f))
        val enterGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        enterGroup.addView(henkan, LinearLayout.LayoutParams(0, dp(46), 1f))
        enterGroup.addView(View(activity), LinearLayout.LayoutParams(dp(14), dp(46)))
        enterGroup.addView(convert, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(enterGroup, LinearLayout.LayoutParams(0, dp(46), 1.75f))

        if (landscapeMode) {
            // The right 2x3 bank is shared with AllTouchDisplayArea. Mouse/Config
            // occupy row 1; these four keyboard buttons occupy rows 2 and 3.
            // Do not add the old bottom row in Landscape.
            val screenW = activity.resources.displayMetrics.widthPixels
            val margin = dp(10); val gap = dp(4)
            val cNotchRadius = dp(64)
            val bankToInfoGap = dp(6)

            // Landscape Info now absorbs the former black C guard area. Keep the
            // historical outer Info edge fixed so the button banks stay aligned.
            val centerX = screenW / 2
            val infoOuterDistance = dp(178)
            val leftInfoX = centerX - infoOuterDistance
            val rightInfoRight = centerX + infoOuterDistance
            val edgeW = (leftInfoX - margin - bankToInfoGap).coerceAtLeast(dp(150))
            val cellW = (edgeW - gap) / 2; val cellH = dp(42); val top = dp(8)
            // Keep the keyboard half of the right 2x3 bank anchored to the root's
            // END edge too.  This must use the same edge-relative rule as
            // AllTouchDisplayArea's Mouse/Config row.
            fun place(v: View, col: Int, rowIndex: Int) {
                root.addView(v, FrameLayout.LayoutParams(cellW, cellH).apply {
                    gravity = Gravity.TOP or Gravity.END
                    marginEnd = if (col == 0) margin + cellW + gap else margin
                    topMargin = top + rowIndex * (cellH + gap)
                })
            }
            // Detach from the temporary LinearLayout before absolute placement.
            listOf(hanZen, muhenkan, henkan, convert).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            place(hanZen, 0, 1); place(muhenkan, 1, 1)
            place(henkan, 0, 2); place(convert, 1, 2)
        } else {
            root.addView(
                row,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                    gravity = Gravity.BOTTOM
                    marginStart = dp(6)
                    marginEnd = dp(6)
                    bottomMargin = dp(7)
                }
            )
        }

        // The former black Landscape C guard Views have been removed. Their visible
        // area is now part of the enlarged left/right Info panes. Since Landscape
        // IME dismissal is explicitly limited to the Mouse button, no separate
        // dead-zone View is needed here.
        landscapeCGuardLeft = null
        landscapeCGuardRight = null

        // C-stick cut-out plate removed: the TouchPad/background now continues directly
        // behind the transparent C lever View. Touch/HID/IME logic is unchanged.
        root.addView(
            lever,
            FrameLayout.LayoutParams(if (landscapeMode) dp(118) else dp(106), if (landscapeMode) dp(118) else dp(106)).apply {
                if (landscapeMode) { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = dp(0) }
                else { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = dp(44) }
            }
        )
        lever.bringToFront()

        hankakuZenkakuButton = hanZen
        muhenkanButton = muhenkan
        directionLever = lever
        henkanButton = henkan
        convertButton = convert
        updateDirectionLeverState()
    }

    private fun activateInputSession() {
        if (!inputControlsEnabled || helpMode || nonInputSurfaceActive) return
        if (landscapeMode) {
            enterLandscapeKeyboardMode()
            return
        }
        val wasActive = inputSessionActive
        inputSessionActive = true
        imeProxy?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        updateDirectionLeverState()
        dismissInputBlocker()
        focusProxyAndShowKeyboard(restartImeConnection = !wasActive)
    }

    private fun suspendInputSession() {
        // Landscape rule: ordinary controls never dismiss the Android IME.
        // Only the explicit Mouse button calls hideKeyboardForOperation(), which
        // in turn enters Mouse mode. This no-op prevents accidental transitions.
        if (landscapeMode) {
            return
        }

        inputSessionActive = false
        updateDirectionLeverState()
        // Help is the only portrait path that fully hides the IME.
        if (helpMode) {
            dismissInputBlocker()
            imeProxy?.clearFocus()
            hideSoftKeyboard()
            return
        }
        focusProxyAndShowKeyboard(showBlockerAfter = true)
    }

    private fun updateDirectionLeverState() {
        directionLever?.invalidate()
    }

    private fun focusProxyAndShowKeyboard(
        showBlockerAfter: Boolean = false,
        restartImeConnection: Boolean = false
    ) {
        if (helpMode || nonInputSurfaceActive) return
        val proxy = imeProxy ?: return
        proxy.post {
            // A show request may have been posted immediately before Help was
            // opened. Re-check here to eliminate that race completely.
            if (helpMode || nonInputSurfaceActive) {
                // In Landscape, never turn a pending keyboard-show race into an
                // implicit keyboard-close. Mouse remains the only close trigger.
                if (!landscapeMode) hideSoftKeyboard()
                return@post
            }
            if (!proxy.hasFocus()) proxy.requestFocus()
            proxy.isCursorVisible = false
            val imm = activity.getSystemService(InputMethodManager::class.java)
            if (restartImeConnection) {
                // Recover cleanly after hardware/external keyboard interaction
                // or a locked session without restarting on every text control.
                imm?.restartInput(proxy)
                lastComposingHidText = ""
                lastStableGodanSource = ""
                compositionMirroredSinceLastCommit = false
                suppressNextImeDelEvents = 0
                suppressImeDelUntilMs = 0L
            }

            // Do not repeatedly ask Android to show an IME that is already on
            // screen. TouchPad and other controls can request the input session
            // many times in quick succession; the duplicate showSoftInput calls
            // only create unnecessary InputMethodManager traffic and animations.
            if (!isAndroidKeyboardVisible()) {
                imm?.showSoftInput(proxy, InputMethodManager.SHOW_IMPLICIT)
            } else {
                Log.v("EIRY_IME", "IME already visible; skip duplicate showSoftInput")
            }
            if (showBlockerAfter || !inputSessionActive) {
                proxy.postDelayed({ showInputBlocker() }, 120L)
            } else {
                dismissInputBlocker()
            }
        }
    }

    private fun isInputSessionActive(): Boolean =
        inputControlsEnabled && inputSessionActive

    private enum class LeverDirection { UP, DOWN, LEFT, RIGHT }

    private class DirectionLeverView(context: android.content.Context) : View(context) {
        var onUnlockRequested: (() -> Unit)? = null
        var onCenter: (() -> Unit)? = null
        var onTextDragStart: (() -> Unit)? = null
        var onTextDragMove: ((Int, Int) -> Unit)? = null
        var onTextDragEnd: (() -> Unit)? = null
        var onDirection: ((LeverDirection) -> Unit)? = null
        var isInputUnlocked: (() -> Boolean)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var knobX = 0f
        private var knobY = 0f
        private var knobReturnAnimator: ValueAnimator? = null
        private var emittedThisGesture = false
        private var gestureStartedLocked = false
        private var textDragActive = false
        private var downX = 0f
        private var downY = 0f
        private var lastDragX = 0f
        private var lastDragY = 0f
        private var currentTouchX = 0f
        private var currentTouchY = 0f
        private var lastCenterTapUpTime = 0L
        private var secondTapHoldCandidate = false
        private var repeatDirection: LeverDirection? = null
        private var repeatStarted = false
        private val doubleTapWindowMs = 320L
        private val directionHoldDelayMs = 420L
        private val directionRepeatIntervalMs = 75L
        private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private val directionRepeatRunnable = object : Runnable {
            override fun run() {
                val direction = repeatDirection ?: return
                if (textDragActive || gestureStartedLocked) return
                repeatStarted = true
                onDirection?.invoke(direction)
                longPressHandler.postDelayed(this, directionRepeatIntervalMs)
            }
        }
        private val textDragLongPress = Runnable {
            if (!gestureStartedLocked && !emittedThisGesture && !textDragActive && secondTapHoldCandidate) {
                stopDirectionRepeat()
                textDragActive = true
                lastDragX = currentTouchX
                lastDragY = currentTouchY
                onTextDragStart?.invoke()
            }
        }

        private fun octagonPath(cx: Float, cy: Float, radius: Float): Path {
            val k = 0.41421356f
            return Path().apply {
                moveTo(cx - radius * k, cy - radius)
                lineTo(cx + radius * k, cy - radius)
                lineTo(cx + radius, cy - radius * k)
                lineTo(cx + radius, cy + radius * k)
                lineTo(cx + radius * k, cy + radius)
                lineTo(cx - radius * k, cy + radius)
                lineTo(cx - radius, cy + radius * k)
                lineTo(cx - radius, cy - radius * k)
                close()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val outerR = minOf(width, height) * 0.43f
            val middleR = outerR * 0.84f
            val wellR = outerR * 0.68f
            val knobR = outerR * 0.46f
            val density = resources.displayMetrics.density

            // No circular base/well under the C stick.
            // The colored knob is drawn directly on the TouchPad/background surface.
            paint.style = Paint.Style.FILL
            paint.shader = null

            paint.color = Win98Style.TEXT
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = density * 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Up", cx, cy - outerR * 0.73f, paint)
            canvas.drawText("Down", cx, cy + outerR * 0.90f, paint)
            canvas.drawText("Left", cx - outerR * 0.82f, cy + paint.textSize * 0.30f, paint)
            canvas.drawText("Right", cx + outerR * 0.82f, cy + paint.textSize * 0.30f, paint)

            val unlocked = isInputUnlocked?.invoke() == true
            val accent = if (unlocked) Color.rgb(62, 190, 92) else Color.rgb(242, 196, 52)
            val kx = cx + knobX
            val ky = cy + knobY
            paint.style = Paint.Style.FILL
            // No black circular base/shadow below the C knob.
            val hi = Color.rgb(
                (Color.red(accent) + 255).coerceAtMost(510) / 2,
                (Color.green(accent) + 255).coerceAtMost(510) / 2,
                (Color.blue(accent) + 255).coerceAtMost(510) / 2
            )
            val lo = Color.rgb(
                Color.red(accent) * 45 / 100,
                Color.green(accent) * 45 / 100,
                Color.blue(accent) * 45 / 100
            )
            paint.shader = RadialGradient(
                kx - knobR * 0.35f, ky - knobR * 0.42f, knobR * 1.45f,
                intArrayOf(Color.WHITE, hi, accent, lo),
                floatArrayOf(0f, 0.18f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(kx, ky, knobR, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * 1.8f
            paint.color = Color.argb(190, 0, 0, 0)
            canvas.drawCircle(kx, ky, knobR, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = knobR * 0.95f
            paint.isFakeBoldText = true
            canvas.drawText("C", kx, ky + paint.textSize * 0.34f, paint)
            paint.isFakeBoldText = false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cx = width / 2f
            val cy = height / 2f
            val maxOffset = minOf(width, height) * 0.28f
            val trigger = minOf(width, height) * 0.36f

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    knobReturnAnimator?.cancel()
                    knobReturnAnimator = null
                    emittedThisGesture = false
                    textDragActive = false
                    stopDirectionRepeat()
                    repeatStarted = false
                    longPressHandler.removeCallbacks(textDragLongPress)
                    downX = event.x
                    downY = event.y
                    currentTouchX = event.x
                    currentTouchY = event.y
                    lastDragX = event.x
                    lastDragY = event.y
                    gestureStartedLocked = isInputUnlocked?.invoke() != true
                    val now = android.os.SystemClock.uptimeMillis()
                    secondTapHoldCandidate = !gestureStartedLocked && lastCenterTapUpTime > 0L && (now - lastCenterTapUpTime) <= doubleTapWindowMs
                    if (gestureStartedLocked) {
                        // First touch only unlocks input; it must not emit a direction.
                        onUnlockRequested?.invoke()
                        invalidate()
                    }
                    updateKnob(event.x - cx, event.y - cy, maxOffset)
                    if (!gestureStartedLocked && secondTapHoldCandidate) {
                        longPressHandler.postDelayed(textDragLongPress, 1_000L)
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    currentTouchX = event.x
                    currentTouchY = event.y
                    updateKnob(event.x - cx, event.y - cy, maxOffset)
                    if (!textDragActive) {
                        val fromDownX = event.x - downX
                        val fromDownY = event.y - downY
                        val distanceFromCenter = sqrt((event.x - cx) * (event.x - cx) + (event.y - cy) * (event.y - cy))
                        // A Text Select long press is valid only while C is held near its center.
                        if (sqrt(fromDownX * fromDownX + fromDownY * fromDownY) >= trigger) {
                            longPressHandler.removeCallbacks(textDragLongPress)
                            secondTapHoldCandidate = false
                        }
                        if (!gestureStartedLocked && distanceFromCenter >= trigger) {
                            val direction = cardinalDirection(event.x - cx, event.y - cy)
                            if (repeatDirection != direction) {
                                stopDirectionRepeat()
                                repeatDirection = direction
                                longPressHandler.postDelayed(directionRepeatRunnable, directionHoldDelayMs)
                            }
                        } else {
                            stopDirectionRepeat()
                        }
                    }
                    if (textDragActive) {
                        val moveX = (event.x - lastDragX).toInt()
                        val moveY = (event.y - lastDragY).toInt()
                        if (moveX != 0 || moveY != 0) {
                            onTextDragMove?.invoke(moveX, moveY)
                            lastDragX = event.x
                            lastDragY = event.y
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(textDragLongPress)
                    val hadRepeated = repeatStarted
                    stopDirectionRepeat()
                    val dx = event.x - cx
                    val dy = event.y - cy
                    if (textDragActive) {
                        onTextDragEnd?.invoke()
                        textDragActive = false
                    } else if (!gestureStartedLocked && !emittedThisGesture) {
                        if (sqrt(dx * dx + dy * dy) >= trigger) {
                            // Flick/short hold = one arrow. A held direction already
                            // emitted repeated arrows, so do not add one more on release.
                            if (!hadRepeated) onDirection?.invoke(cardinalDirection(dx, dy))
                            emittedThisGesture = true
                        } else {
                            onCenter?.invoke()
                            val now = android.os.SystemClock.uptimeMillis()
                            if (secondTapHoldCandidate) {
                                lastCenterTapUpTime = 0L
                            } else {
                                lastCenterTapUpTime = now
                            }
                        }
                    }
                    secondTapHoldCandidate = false
                    animateKnobHome()
                    performClick()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(textDragLongPress)
                    stopDirectionRepeat()
                    if (textDragActive) {
                        onTextDragEnd?.invoke()
                        textDragActive = false
                    }
                    secondTapHoldCandidate = false
                    animateKnobHome()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun animateKnobHome() {
            val startX = knobX
            val startY = knobY
            knobReturnAnimator?.cancel()
            if (startX == 0f && startY == 0f) {
                invalidate()
                return
            }
            knobReturnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 125L
                addUpdateListener { animator ->
                    val t = animator.animatedFraction
                    val eased = 1f - (1f - t) * (1f - t)
                    knobX = startX * (1f - eased)
                    knobY = startY * (1f - eased)
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        knobX = 0f; knobY = 0f; knobReturnAnimator = null; invalidate()
                    }
                })
                start()
            }
        }

        private fun stopDirectionRepeat() {
            longPressHandler.removeCallbacks(directionRepeatRunnable)
            repeatDirection = null
        }

        private fun updateKnob(dx: Float, dy: Float, maxOffset: Float) {
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= maxOffset || distance == 0f) {
                knobX = dx
                knobY = dy
            } else {
                val angle = atan2(dy, dx)
                knobX = cos(angle) * maxOffset
                knobY = sin(angle) * maxOffset
            }
            invalidate()
        }

        private fun cardinalDirection(dx: Float, dy: Float): LeverDirection =
            if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                if (dx >= 0f) LeverDirection.RIGHT else LeverDirection.LEFT
            } else {
                if (dy >= 0f) LeverDirection.DOWN else LeverDirection.UP
            }
    }

    private fun resolveBlockerHeightPx(): Int {
        // 1) Live IME measurement (preferred).
        val root = rootView
        val liveImeHeight = root?.let {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsets(WindowInsetsCompat.Type.ime())
                ?.bottom
        } ?: 0
        if (liveImeHeight > 0) {
            lastImeHeightPx = liveImeHeight
            return liveImeHeight
        }

        // 2) Last successfully measured IME height. This keeps the reserved
        // lower area stable even during a transient frame where Android reports 0.
        if (lastImeHeightPx > 0) {
            return lastImeHeightPx
        }

        // 3) First-launch / measurement-failure fallback.
        return (activity.resources.displayMetrics.heightPixels * 0.40f)
            .toInt()
            .coerceAtLeast(1)
    }

    private fun showInputBlocker() {
        // Keyboard lock exists only in Portrait mode.
        if (landscapeMode) {
            dismissInputBlocker()
            return
        }
        if (isInputSessionActive()) {
            dismissInputBlocker()
            return
        }
        val anchor = rootView ?: return
        if (!anchor.isAttachedToWindow) return

        val blockerHeight = resolveBlockerHeightPx()

        val existing = inputBlockerPopup
        if (existing != null) {
            existing.height = blockerHeight
            if (!existing.isShowing) {
                existing.showAtLocation(anchor, Gravity.BOTTOM, 0, 0)
            } else {
                existing.update(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    blockerHeight
                )
            }
            return
        }

        val blocker = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(155, 0, 0, 0))
            isClickable = true
            isFocusable = false
            alpha = 0f
            setOnTouchListener { _, _ -> true }

            val clock = TextView(activity).apply {
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                isClickable = false
            }
            blockerClockView = clock
            addView(clock, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        blockerClockHandler.removeCallbacks(blockerClockRunnable)
        blockerClockHandler.post(blockerClockRunnable)
        blocker.animate().alpha(1f).setDuration(220L).start()

        inputBlockerPopup = PopupWindow(
            blocker,
            FrameLayout.LayoutParams.MATCH_PARENT,
            blockerHeight,
            false
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = true
            isOutsideTouchable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAtLocation(anchor, Gravity.BOTTOM, 0, 0)
        }
    }

    private fun dismissInputBlocker() {
        blockerClockHandler.removeCallbacks(blockerClockRunnable)
        blockerClockView = null
        inputBlockerPopup?.dismiss()
        inputBlockerPopup = null
    }

    private fun hideSoftKeyboard() {
        val token = imeProxy?.windowToken ?: return
        val imm = activity.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(token, 0)
        rootView?.let { ViewCompat.requestApplyInsets(it) }
    }
}

