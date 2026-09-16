package com.example.diazymouse.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.diazymouse.connection.socket.MouseConnection
import com.example.diazymouse.settings.model.OperationMode
import com.example.diazymouse.ui.UiDimensions
import com.example.diazymouse.ui.Win98Style

/**
 * DiazyMouse v1.1 connection screen.
 * Classic exposes ADB only. AllTouch exposes Bluetooth only.
 * Old BluetoothTest UI is intentionally removed from the product path.
 */
class ConnectionScreenArea(
    private val activity: ComponentActivity,
    private val dimensions: UiDimensions,
    private val mouseConnection: MouseConnection,
    private val bluetoothHid: BluetoothHidBridge,
    private val modeProvider: () -> OperationMode,
    private val onBackToMain: () -> Unit
) {
    private lateinit var overlay: FrameLayout
    private lateinit var content: FrameLayout

    fun addTo(root: FrameLayout) {
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(Win98Style.DESKTOP)
            visibility = View.GONE
            isClickable = true
        }
        val topMarginPx = (dimensions.screenHeightPx * 0.10f).toInt()
        val overlayHeightPx = dimensions.screenHeightPx - topMarginPx
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
        overlay.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    fun show() {
        if (!::overlay.isInitialized) return
        buildPage()
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.animate().cancel()
        overlay.alpha = 0f
        overlay.scaleX = 0.992f
        overlay.scaleY = 0.992f
        overlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190L).start()
    }

    private fun buildPage() {
        content.removeAllViews()
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Win98Style.FACE)
            if (isLandscape()) setPadding(dp(12), dp(3), dp(12), dp(5))
            else setPadding(24, 16, 24, 24)
        }

        page.addView(button("BACK") {
            hide()
            onBackToMain()
        }, fullWidthWrap())

        val mode = modeProvider()
        val connected = when (mode) {
            OperationMode.CLASSIC -> mouseConnection.currentPcName() != "Not connected"
            OperationMode.ALL_TOUCH -> bluetoothHid.isConnected()
        }
        val target = when (mode) {
            OperationMode.CLASSIC -> mouseConnection.currentPcName().takeUnless { it == "Not connected" } ?: "Unknown"
            OperationMode.ALL_TOUCH -> if (bluetoothHid.isConnected()) bluetoothHid.connectedTargetName() else "Unknown"
        }

        page.addView(connectionStatusPanel(connected, target, mode), fullWidthWrap())

        if (mode == OperationMode.ALL_TOUCH) {
            val bonded = bluetoothHid.bondedDevices()
            if (bonded.isEmpty()) {
                page.addView(label("Paired PC: none", 14f, Color.LTGRAY), fullWidthWrap())
                page.addView(button("Open Bluetooth Settings") {
                    bluetoothHid.openBluetoothSettings()
                }, fullWidthWrap())
            } else {
                page.addView(label("Paired PCs", 16f), fullWidthWrap())
                bonded.forEach { device ->
                    val name = safeBluetoothDeviceName(device)
                    val address = safeBluetoothDeviceAddress(device)
                    page.addView(button("Connect: $name\n$address") {
                        bluetoothHid.preparePcConnection()
                        bluetoothHid.connect(device)
                        buildPage()
                    }, fullWidthWrap())
                }
            }
        }

        page.addView(button("AUTO CONNECT") {
            when (modeProvider()) {
                OperationMode.CLASSIC -> mouseConnection.connectAndRequestPcName()
                OperationMode.ALL_TOUCH -> bluetoothHid.requestAutoConnect()
            }
            buildPage()
        }, fullWidthWrap())

        // Always visible by requirement.
        page.addView(button("DISCONNECT") {
            when (modeProvider()) {
                OperationMode.CLASSIC -> mouseConnection.disconnect()
                OperationMode.ALL_TOUCH -> bluetoothHid.disconnect()
            }
            buildPage()
        }, fullWidthWrap())

        content.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(page, FrameLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
    }

    fun refreshBluetoothState() {
        if (::overlay.isInitialized && overlay.visibility == View.VISIBLE) buildPage()
    }

    fun release() = Unit

    private fun hide() { overlay.visibility = View.GONE }

    private fun button(value: String, action: () -> Unit) = Button(activity).apply {
        text = value
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(Win98Style.TEXT)
        background = Win98Style.buttonBackground(dp(2))
        elevation = 0f
        stateListAnimator = null
        // Keep Connect screen buttons the same visual size as Config -> Setting.
        // Do not apply the old landscape-only compact height/padding override.
        minWidth = 0
        minHeight = 0
        setOnClickListener { action() }
    }

    private fun canReadBluetoothDeviceInfo(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    private fun safeBluetoothDeviceName(device: BluetoothDevice): String =
        if (!canReadBluetoothDeviceInfo()) {
            "Unknown"
        } else {
            runCatching { device.name ?: "Unknown" }.getOrDefault("Unknown")
        }

    @SuppressLint("MissingPermission")
    private fun safeBluetoothDeviceAddress(device: BluetoothDevice): String =
        if (!canReadBluetoothDeviceInfo()) {
            "unknown"
        } else {
            runCatching { device.address }.getOrDefault("unknown")
        }

    private fun connectionStatusPanel(
        connected: Boolean,
        target: String,
        mode: OperationMode
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val compact = isLandscape()
        setPadding(dp(10), if (compact) dp(2) else dp(8), dp(10), if (compact) dp(2) else dp(8))
        background = Win98Style.sunkenBackground(dp(2), Color.rgb(242, 242, 242))
        elevation = 0f
        addView(label("CONNECTION STATUS", if (compact) 12f else 13f, Win98Style.TITLE_BLUE), fullWidthWrap())
        addView(label(if (connected) "Connecting" else "NoConnecting", if (compact) 15f else 19f, Win98Style.TEXT), fullWidthWrap())
        addView(label("Target: $target", if (compact) 13f else 15f, Win98Style.TEXT), fullWidthWrap())
        addView(label(if (mode == OperationMode.CLASSIC) "ADB" else "Bluetooth", if (compact) 13f else 16f, Win98Style.TEXT), fullWidthWrap())
    }

    private fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(activity).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(color)
        setPadding(dp(8), if (isLandscape()) dp(1) else dp(5), dp(8), if (isLandscape()) dp(1) else dp(5))
    }

    private fun isLandscape(): Boolean =
        activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun fullWidthWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(5)
    }
}
