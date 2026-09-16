package com.example.diazymouse.connection

import com.example.diazymouse.connection.socket.MouseConnection

/**
 * Concrete router that can be switched between ADB, Bluetooth, both, or none.
 *
 * This keeps the UI and gesture layer decoupled from any single transport while
 * still preserving the existing MouseConnection + BluetoothHidBridge wiring.
 */
class MainInputTransport(
    private val mouseConnection: MouseConnection,
    private val bluetoothHid: BluetoothHidBridge
) : InputTransport, TransportSwitchable {
    private val adbTransport = AdbInputTransport(mouseConnection)
    private val bluetoothTransport = BluetoothInputTransport(bluetoothHid)

    private var mode: TransportMode = TransportMode.BOTH
    private var adbEnabled = true
    private var bluetoothEnabled = true

    override fun setMode(mode: TransportMode) {
        this.mode = mode
        when (mode) {
            TransportMode.ADB_ONLY -> {
                adbEnabled = true
                bluetoothEnabled = false
            }
            TransportMode.BLUETOOTH_ONLY -> {
                adbEnabled = false
                bluetoothEnabled = true
            }
            TransportMode.BOTH -> {
                adbEnabled = true
                bluetoothEnabled = true
            }
            TransportMode.NONE -> {
                adbEnabled = false
                bluetoothEnabled = false
            }
        }
    }

    override fun setAdbEnabled(enabled: Boolean) {
        adbEnabled = enabled
        if (mode == TransportMode.BOTH && !enabled) mode = TransportMode.BLUETOOTH_ONLY
        if (mode == TransportMode.ADB_ONLY && enabled) mode = TransportMode.BOTH
    }

    override fun setBluetoothEnabled(enabled: Boolean) {
        bluetoothEnabled = enabled
        if (mode == TransportMode.BOTH && !enabled) mode = TransportMode.ADB_ONLY
        if (mode == TransportMode.BLUETOOTH_ONLY && enabled) mode = TransportMode.BOTH
    }

    override fun isConnected(): Boolean =
        (adbEnabled && adbTransport.isConnected()) || (bluetoothEnabled && bluetoothTransport.isConnected())

    private fun adbActive(): Boolean = adbEnabled && adbTransport.isConnected()
    private fun bluetoothActive(): Boolean = bluetoothEnabled && bluetoothTransport.isConnected()

    override fun move(dx: Int, dy: Int) {
        if (adbActive()) adbTransport.move(dx, dy)
        if (bluetoothActive()) bluetoothTransport.move(dx, dy)
    }

    override fun scroll(amount: Int) {
        if (adbActive()) adbTransport.scroll(amount)
        if (bluetoothActive()) bluetoothTransport.scroll(amount)
    }

    override fun horizontalScroll(amount: Int) {
        if (adbActive()) adbTransport.horizontalScroll(amount)
        if (bluetoothActive()) bluetoothTransport.horizontalScroll(amount)
    }

    override fun leftClick() {
        if (adbActive()) adbTransport.leftClick()
        if (bluetoothActive()) bluetoothTransport.leftClick()
    }

    override fun rightClick() {
        if (adbActive()) adbTransport.rightClick()
        if (bluetoothActive()) bluetoothTransport.rightClick()
    }

    override fun middleClick() {
        if (adbActive()) adbTransport.middleClick()
        if (bluetoothActive()) bluetoothTransport.middleClick()
    }

    override fun leftDown() {
        if (adbActive()) adbTransport.leftDown()
        if (bluetoothActive()) bluetoothTransport.leftDown()
    }

    override fun leftUp() {
        if (adbActive()) adbTransport.leftUp()
        if (bluetoothActive()) bluetoothTransport.leftUp()
    }

    override fun back() {
        if (adbActive()) adbTransport.back()
        if (bluetoothActive()) bluetoothTransport.back()
    }

    override fun forward() {
        if (adbActive()) adbTransport.forward()
        if (bluetoothActive()) bluetoothTransport.forward()
    }

    override fun doubleLeftClick() {
        if (adbActive()) adbTransport.doubleLeftClick()
        if (bluetoothActive()) bluetoothTransport.doubleLeftClick()
    }

    override fun zoomIn() {
        if (adbActive()) adbTransport.zoomIn()
        if (bluetoothActive()) bluetoothTransport.zoomIn()
    }

    override fun zoomOut() {
        if (adbActive()) adbTransport.zoomOut()
        if (bluetoothActive()) bluetoothTransport.zoomOut()
    }

    override fun startActiveWindowSelection(previous: Boolean) {
        if (adbActive()) adbTransport.startActiveWindowSelection(previous)
        if (bluetoothActive()) bluetoothTransport.startActiveWindowSelection(previous)
    }

    override fun stepActiveWindowSelection(previous: Boolean) {
        if (adbActive()) adbTransport.stepActiveWindowSelection(previous)
        if (bluetoothActive()) bluetoothTransport.stepActiveWindowSelection(previous)
    }

    override fun commitActiveWindowSelection() {
        if (adbActive()) adbTransport.commitActiveWindowSelection()
        if (bluetoothActive()) bluetoothTransport.commitActiveWindowSelection()
    }

    override fun hankakuZenkaku() {
        if (adbActive()) adbTransport.hankakuZenkaku()
        if (bluetoothActive()) bluetoothTransport.hankakuZenkaku()
    }

    override fun enter() {
        if (adbActive()) adbTransport.enter()
        if (bluetoothActive()) bluetoothTransport.enter()
    }

    override fun henkan() {
        if (adbActive()) adbTransport.henkan()
        if (bluetoothActive()) bluetoothTransport.henkan()
    }

    override fun refreshF5() {
        if (adbActive()) adbTransport.refreshF5()
        if (bluetoothActive()) bluetoothTransport.refreshF5()
    }

    override fun home() {
        if (adbActive()) adbTransport.home()
        if (bluetoothActive()) bluetoothTransport.home()
    }

    override fun end() {
        if (adbActive()) adbTransport.end()
        if (bluetoothActive()) bluetoothTransport.end()
    }

    override fun printWholeScreen() {
        if (adbActive()) adbTransport.printWholeScreen()
        if (bluetoothActive()) bluetoothTransport.printWholeScreen()
    }

    override fun printActiveWindow() {
        if (adbActive()) adbTransport.printActiveWindow()
        if (bluetoothActive()) bluetoothTransport.printActiveWindow()
    }

    override fun muteAllTouch() {
        if (adbActive()) adbTransport.muteAllTouch()
        if (bluetoothActive()) bluetoothTransport.muteAllTouch()
    }

    override fun mute() {
        if (adbActive()) adbTransport.mute()
        if (bluetoothActive()) bluetoothTransport.mute()
    }

    override fun previousActiveWindow() {
        if (adbActive()) adbTransport.previousActiveWindow()
        if (bluetoothActive()) bluetoothTransport.previousActiveWindow()
    }

    override fun nextActiveWindow() {
        if (adbActive()) adbTransport.nextActiveWindow()
        if (bluetoothActive()) bluetoothTransport.nextActiveWindow()
    }

    override fun openBrowserSearch() {
        if (adbActive()) adbTransport.openBrowserSearch()
        if (bluetoothActive()) bluetoothTransport.openBrowserSearch()
    }

    override fun find() {
        if (adbActive()) adbTransport.find()
        if (bluetoothActive()) bluetoothTransport.find()
    }

    override fun copy() {
        if (adbActive()) adbTransport.copy()
        if (bluetoothActive()) bluetoothTransport.copy()
    }

    override fun paste() {
        if (adbActive()) adbTransport.paste()
        if (bluetoothActive()) bluetoothTransport.paste()
    }

    override fun undo() { if (adbActive()) adbTransport.undo(); if (bluetoothActive()) bluetoothTransport.undo() }
    override fun redo() { if (adbActive()) adbTransport.redo(); if (bluetoothActive()) bluetoothTransport.redo() }
    override fun escapeKey() { if (adbActive()) adbTransport.escapeKey(); if (bluetoothActive()) bluetoothTransport.escapeKey() }
    override fun showDesktop() { if (adbActive()) adbTransport.showDesktop(); if (bluetoothActive()) bluetoothTransport.showDesktop() }
    override fun cut() { if (adbActive()) adbTransport.cut(); if (bluetoothActive()) bluetoothTransport.cut() }
    override fun selectAll() { if (adbActive()) adbTransport.selectAll(); if (bluetoothActive()) bluetoothTransport.selectAll() }
    override fun deleteKey() { if (adbActive()) adbTransport.deleteKey(); if (bluetoothActive()) bluetoothTransport.deleteKey() }
    override fun minimizeWindow() { if (adbActive()) adbTransport.minimizeWindow(); if (bluetoothActive()) bluetoothTransport.minimizeWindow() }
    override fun maximizeWindow() { if (adbActive()) adbTransport.maximizeWindow(); if (bluetoothActive()) bluetoothTransport.maximizeWindow() }
    override fun previousTab() { if (adbActive()) adbTransport.previousTab(); if (bluetoothActive()) bluetoothTransport.previousTab() }
    override fun nextTab() { if (adbActive()) adbTransport.nextTab(); if (bluetoothActive()) bluetoothTransport.nextTab() }
    /** EiryMouse Bluetooth-HID modifier controls. Current EiryMouse mode is Bluetooth-only. */
    fun holdKeyboardModifiers(modifiers: Byte) {
        if (bluetoothActive()) bluetoothHid.holdModifiers(modifiers)
    }

    fun releaseKeyboardModifiers() {
        if (bluetoothActive()) bluetoothHid.releaseKeyboard()
    }

    fun pressHidKey(key: Byte, modifiers: Byte = 0) {
        if (bluetoothActive()) bluetoothHid.pressKey(key, modifiers)
    }

}

