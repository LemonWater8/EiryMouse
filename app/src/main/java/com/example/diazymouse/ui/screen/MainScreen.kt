package com.example.diazymouse.ui.screen

import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.view.Gravity
import androidx.core.view.ViewCompat
import androidx.activity.ComponentActivity
import com.example.diazymouse.connection.BluetoothHidBridge
import com.example.diazymouse.connection.ConnectionScreenArea
import com.example.diazymouse.connection.MainInputTransport
import com.example.diazymouse.connection.TransportMode
import com.example.diazymouse.connection.socket.MouseConnection
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.input.MainInputArea
import com.example.diazymouse.settings.store.KeepScreenOnSettingsStore
import com.example.diazymouse.settings.store.FloatingSettingsStore
import com.example.diazymouse.settings.store.OperationModeSettingsStore
import com.example.diazymouse.settings.model.OperationMode
import com.example.diazymouse.ui.UiConfig
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.component.FunctionKeyArea
import com.example.diazymouse.ui.component.ManagementHubArea
import com.example.diazymouse.ui.component.SideControlArea
import com.example.diazymouse.ui.screen.SettingsScreenArea
import com.example.diazymouse.app.MainActivity

class MainScreen(
    private val activity: ComponentActivity,
    private val mouseConnection: MouseConnection
) {
    private var dimensions =
        UiDimensions.from(activity.resources.displayMetrics)

    private var rootView: FrameLayout? = null
    private var keyboardUiController: KeyboardUiController? = null
    private var configButtonController: ConfigButtonController? = null

    private var connectionScreenArea: ConnectionScreenArea? = null
    private var touchPad: FrameLayout? = null
    private var allTouchDisplayArea: AllTouchDisplayArea? = null
    private var managementHubArea: ManagementHubArea? = null

    // IMPORTANT: BluetoothHidBridge registers an Activity Result launcher in
    // its constructor. That registration must happen while MainActivity is still
    // in its initial onCreate lifecycle, not while the activity is RESUMED.
    // Keep one bridge for the lifetime of MainScreen and reuse it when the UI is
    // rebuilt after an orientation change.
    private val bluetoothHid = BluetoothHidBridge(
        activity = activity,
        onStateChanged = {
            refreshConnectionPresentation()
            connectionScreenArea?.refreshBluetoothState()
        }
    )

    private val inputTransport = MainInputTransport(
        mouseConnection = mouseConnection,
        bluetoothHid = bluetoothHid
    )

    fun create(): FrameLayout {
        // create() is also used for mode changes and orientation changes.
        allTouchDisplayArea = null
        touchPad = null
        // Orientation rebuild must dispose the old IME/blocker controller first.
        // Otherwise a portrait keyboard-lock PopupWindow can survive into Landscape.
        keyboardUiController?.release()
        keyboardUiController = null
        connectionScreenArea?.release()
        connectionScreenArea = null

        val root = object : FrameLayout(activity) {
            private var cDeadZoneGesture = false

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // The empty strips immediately beside C are intentionally inert in
                // Landscape. Consume the whole gesture here so it cannot briefly
                // enter mouse mode, hide/show the IME, or leak into the TouchPad.
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    cDeadZoneGesture = keyboardUiController?.isLandscapeCDeadZoneTouch(
                        event.rawX, event.rawY
                    ) == true
                    if (cDeadZoneGesture) {
                        // If the IME is already open, this guard tap must behave
                        // exactly like touching nothing: keep the current typing
                        // session alive and consume the complete gesture.
                        keyboardUiController?.preserveLandscapeKeyboardOnCGuardTap()
                        android.util.Log.v(
                            "EIRY_TOUCH",
                            "Landscape C dead-zone DOWN ignored x=${event.rawX.toInt()} y=${event.rawY.toInt()}"
                        )
                        return true
                    }
                } else if (cDeadZoneGesture) {
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        cDeadZoneGesture = false
                    }
                    return true
                }

                // Keep typing only for the dedicated keyboard controls. Any
                // other app-side touch suspends text input and restores the
                // dark blocker over the Android IME.
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // A/B sticks are keyboard-session-safe controls. They may
                    // execute mouse/navigation functions without forcing the
                    // Android IME back into LOCK.
                    val isStick = allTouchDisplayArea?.isStickTouch(event.rawX, event.rawY) == true
                    val isLandscapeTouchPad =
                        (activity as? MainActivity)?.isLandscapeMode() == true &&
                            allTouchDisplayArea?.isTouchPadTouch(event.rawX, event.rawY) == true
                    // In Landscape the TouchPad must keep the same MotionEvent stream.
                    // Hiding the IME synchronously on ACTION_DOWN can resize the window
                    // and disrupt that stream. The explicit Operation button owns IME close.
                    if (!isStick && !isLandscapeTouchPad) {
                        keyboardUiController?.handleAppTouch(event.rawX, event.rawY)
                    }
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setBackgroundColor(UiConfig.ROOT_BACKGROUND)
        }
        rootView = root
        val operationMode = OperationMode.ALL_TOUCH
        OperationModeSettingsStore.set(activity, OperationMode.ALL_TOUCH)
        inputTransport.setMode(TransportMode.BLUETOOTH_ONLY)
        applyKeepScreenOn(KeepScreenOnSettingsStore.isEnabled(activity))

        val settingsScreen = SettingsScreenArea(
            activity = activity,
            dimensions = dimensions,
            onBackToMain = {
                showConfigTop()
            }
        )
        settingsScreen.addTo(root)

        var managementHub: ManagementHubArea? = null

        val allTouch = AllTouchDisplayArea(
            activity = activity,
            dimensions = dimensions,
            inputTransport = inputTransport,
            onToggleKeyboardWithHanZen = {
                toggleKeyboardWithHanZen()
            },
            onOpenConfig = {
                enterConfigMode()
                managementHub?.show()
            },
            onConfigLongPress = {
                if (bluetoothHid.isConnected()) {
                    bluetoothHid.disconnect()
                    allTouchDisplayArea?.showSinglePress("Disconnect")
                } else {
                    bluetoothHid.requestAutoConnect()
                    allTouchDisplayArea?.showSinglePress("AutoConnect")
                }
            },
            onFloating = {
                (activity as? MainActivity)?.showDesktopAtMarkOut()
            },
            onFloatingLockChanged = { locked ->
                (activity as? MainActivity)?.setFloatingLocked(locked)
            },
            onOperation = {
                keyboardUiController?.hideKeyboardForOperation()
            },
            onHelpOpened = {
                keyboardUiController?.enterHelpMode()
                (activity as? MainActivity)?.setFloatingUiSurfaceActive(true)
            },
            onHelpClosed = {
                keyboardUiController?.exitHelpMode()
                (activity as? MainActivity)?.setFloatingUiSurfaceActive(false)
            },
            onHelpActivity = {
                (activity as? MainActivity)?.pulseFloatingInteraction()
            }
        )
        allTouchDisplayArea = allTouch
        allTouch.addTo(root)

        // Add the compact keyboard-control row after the AllTouch surface.
        // The visible TextArea/Clear controls are intentionally removed; a hidden
        // IME proxy keeps Godan input and realtime HID forwarding alive.
        val keyboardUi = KeyboardUiController(
            activity = activity,
            bluetoothHid = bluetoothHid,
            landscapeMode = (activity as? MainActivity)?.isLandscapeMode() == true,
            onSinglePress = { name ->
                allTouchDisplayArea?.showSinglePress(name)
            },
            onTextDragStart = {
                allTouchDisplayArea?.startCLeverTextDrag()
            },
            onTextDragMove = { dx, dy ->
                allTouchDisplayArea?.moveCLeverTextDrag(dx, dy)
            },
            onTextDragEnd = {
                allTouchDisplayArea?.endCLeverTextDrag()
            }
        )
        keyboardUiController = keyboardUi
        keyboardUi.attach(root)

        mouseConnection.setStateListener {
            refreshConnectionPresentation()
        }
        refreshConnectionPresentation()

        val connectionScreen = ConnectionScreenArea(
            activity = activity,
            dimensions = dimensions,
            mouseConnection = mouseConnection,
            bluetoothHid = bluetoothHid,
            modeProvider = { OperationModeSettingsStore.get(activity) },
            onBackToMain = {
                managementHub?.show()
            }
        )
        connectionScreenArea = connectionScreen
        connectionScreen.addTo(root)

        managementHub = ManagementHubArea(
            activity = activity,
            dimensions = dimensions,
            onConnect = {
                connectionScreen.show()
            },
            onMarkPosition = {
                (activity as? MainActivity)?.showMarkPositionEditor()
            },
            onOpenVibration = {
                enterConfigMode()
                settingsScreen.showVibration()
            },
            onOpenMouse = {
                enterConfigMode()
                settingsScreen.showMouse()
            },
            modeProvider = { OperationMode.ALL_TOUCH },
            onModeChanged = { },
            statusProvider = {
                val connected = bluetoothHid.isConnected()
                Pair(connected, if (connected) bluetoothHid.connectedTargetName() else "Unknown")
            },
            onDisconnect = {
                if (bluetoothHid.isConnected()) {
                    bluetoothHid.disconnect()
                }
                if (mouseConnection.currentPcName() != "Not connected") {
                    mouseConnection.disconnect()
                }
            },
            screenAwakeProvider = {
                KeepScreenOnSettingsStore.isEnabled(activity)
            },
            onScreenAwakeChanged = { enabled ->
                KeepScreenOnSettingsStore.setEnabled(activity, enabled)
                applyKeepScreenOn(enabled)
            },
            onFinishConfirmed = {
                if (mouseConnection.currentPcName() != "Not connected") {
                    mouseConnection.requestRemoteExit()
                }
                mouseConnection.stop()
                bluetoothHid.disconnect()
                (activity as? MainActivity)?.requestCompleteExit()
                    ?: activity.finishAndRemoveTask()
            },
            orientationLabelProvider = {
                (activity as? MainActivity)?.orientationModeLabel() ?: "PORTRAIT"
            },
            onToggleOrientation = {
                // Stop IME + keyboard lock first, regardless of current visibility,
                // then rotate. The destination is rebuilt into Config Top.
                keyboardUiController?.prepareForOrientationChange()
                (activity as? MainActivity)?.toggleScreenOrientation()
            },
            onFinishScreenEntered = {
                keyboardUiController?.enterHelpMode()
            },
            onFinishScreenExited = {
                keyboardUiController?.exitHelpMode()
            },
            onBackToMain = {
                exitConfigMode()
            }
        )
        managementHub.addTo(root)
        managementHubArea = managementHub

        // Config is now part of the fixed top bar inside AllTouchDisplayArea.

        return root
    }

    private fun rebuildVisibleUi() {
        activity.runOnUiThread {
            dimensions = UiDimensions.from(activity.resources.displayMetrics)
            activity.setContentView(create())
        }
    }

    private fun hideConfigButton() {
        configButtonController?.hide()
    }

    private fun showConfigButton() {
        configButtonController?.show()
    }

    private fun setInputUiEnabled(enabled: Boolean) {
        keyboardUiController?.setInputControlsEnabled(enabled)
    }

    private fun enterConfigMode() {
        setInputUiEnabled(false)
        hideConfigButton()
        allTouchDisplayArea?.setConfigMode(true)
        (activity as? MainActivity)?.setFloatingUiSurfaceActive(true)
    }

    private fun exitConfigMode() {
        allTouchDisplayArea?.setConfigMode(false)
        setInputUiEnabled(true)
        showConfigButton()
        (activity as? MainActivity)?.setFloatingUiSurfaceActive(false)
    }

    private fun refreshConnectionPresentation() {
        activity.runOnUiThread {
            val visualState = when {
                bluetoothHid.hasConnectionFault() ->
                    AllTouchDisplayArea.ConnectionVisualState.ERROR
                bluetoothHid.isConnected() ->
                    AllTouchDisplayArea.ConnectionVisualState.CONNECTED
                bluetoothHid.isAutoConnectPending() ->
                    AllTouchDisplayArea.ConnectionVisualState.CONNECTING
                else ->
                    AllTouchDisplayArea.ConnectionVisualState.IDLE
            }
            allTouchDisplayArea?.setConnectionVisualState(visualState)
        }
    }


    fun setFloatingTransparency(active: Boolean) {
        activity.runOnUiThread {
            rootView?.setBackgroundColor(if (active) Color.TRANSPARENT else UiConfig.ROOT_BACKGROUND)
            allTouchDisplayArea?.setFloatingTransparency(active)
        }
    }

    fun onHostResume(floatingActive: Boolean) {
        activity.runOnUiThread {
            if (floatingActive) {
                rootView?.setBackgroundColor(Color.TRANSPARENT)
                allTouchDisplayArea?.setFloatingTransparency(true)
            } else {
                rootView?.setBackgroundColor(UiConfig.ROOT_BACKGROUND)
                allTouchDisplayArea?.restoreSavedBackgroundOnResume()
                if ((activity as? MainActivity)?.isLandscapeMode() == true) {
                    // Landscape rule: returning to the app must not alter IME visibility.
                    // Only the explicit Mouse button is allowed to close an open IME.
                } else {
                    keyboardUiController?.ensureKeyboardVisible()
                }
            }
        }
    }

    fun onHostCollapseForFloating() {
        activity.runOnUiThread {
            allTouchDisplayArea?.suspendSavedBackgroundForFloating()
        }
    }

    fun showConfigTop() {
        activity.runOnUiThread {
            enterConfigMode()
            managementHubArea?.show()
        }
    }


    fun onConfigurationChanged() {
        dimensions = UiDimensions.from(activity.resources.displayMetrics)

        // Rebuild the visible hierarchy so the actual metric change is reflected
        // in the layout; otherwise the old portrait-sized views remain in place
        // and the landscape screen appears heavily distorted.
        rebuildVisibleUi()

        // Landscape is a configuration-only landing surface after rotation.
        // Never expose the normal keyboard/keyboard-lock UI immediately after entering Landscape.
        if ((activity as? MainActivity)?.isLandscapeMode() == true) {
            showConfigTop()
        }
    }

    internal fun toggleKeyboardWithHanZen(): Boolean =
        keyboardUiController?.toggleKeyboardWithHanZen() ?: false

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun release() {
        mouseConnection.setStateListener(null)
        connectionScreenArea?.release()
        bluetoothHid.release()
    }
}
