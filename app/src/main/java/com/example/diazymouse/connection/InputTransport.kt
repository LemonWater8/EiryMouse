package com.example.diazymouse.connection

import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.bhid.report.HidMousePacket
import com.example.diazymouse.connection.protocol.MouseCommand
import com.example.diazymouse.connection.socket.MouseConnection

interface InputTransport {
    fun isConnected(): Boolean
    fun move(dx: Int, dy: Int)
    fun scroll(amount: Int)
    fun horizontalScroll(amount: Int)
    fun leftClick()
    fun rightClick()
    fun middleClick()
    fun leftDown()
    fun leftUp()
    fun back()
    fun forward()
    fun doubleLeftClick()
    fun zoomIn()
    fun zoomOut()
    fun startActiveWindowSelection(previous: Boolean)
    fun stepActiveWindowSelection(previous: Boolean)
    fun commitActiveWindowSelection()
    fun hankakuZenkaku()
    fun enter()
    fun henkan()
    fun refreshF5()
    fun home()
    fun end()
    fun printWholeScreen()
    fun printActiveWindow()
    fun muteAllTouch()
    fun mute()
    fun previousActiveWindow()
    fun nextActiveWindow()
    fun openBrowserSearch()
    fun find()
    fun copy()
    fun paste()
    fun undo()
    fun redo()
    fun escapeKey()
    fun showDesktop()
    fun cut()
    fun selectAll()
    fun deleteKey()
    fun minimizeWindow()
    fun maximizeWindow()
    fun previousTab()
    fun nextTab()
}

enum class TransportMode { ADB_ONLY, BLUETOOTH_ONLY, BOTH, NONE }

interface TransportSwitchable {
    fun setMode(mode: TransportMode)
    fun setAdbEnabled(enabled: Boolean)
    fun setBluetoothEnabled(enabled: Boolean)
}

class AdbInputTransport(private val mouseConnection: MouseConnection) : InputTransport {
    override fun isConnected() = mouseConnection.currentPcName() != "Not connected"
    override fun move(dx: Int, dy: Int) = mouseConnection.sendMove(dx, dy)
    override fun scroll(amount: Int) = mouseConnection.sendCommand(MouseCommand.scroll(amount))
    override fun horizontalScroll(amount: Int) = mouseConnection.sendCommand(MouseCommand.horizontalScroll(amount))
    override fun leftClick() = mouseConnection.sendCommand(MouseCommand.LEFT_CLICK)
    override fun rightClick() = mouseConnection.sendCommand(MouseCommand.RIGHT_CLICK)
    override fun middleClick() = mouseConnection.sendCommand(MouseCommand.MIDDLE_CLICK)
    override fun leftDown() = mouseConnection.sendCommand(MouseCommand.LEFT_DOWN)
    override fun leftUp() = mouseConnection.sendCommand(MouseCommand.LEFT_UP)
    override fun back() = mouseConnection.sendCommand(MouseCommand.BACK)
    override fun forward() = mouseConnection.sendCommand(MouseCommand.FORWARD)
    override fun doubleLeftClick() = mouseConnection.sendCommand(MouseCommand.DOUBLE_LEFT_CLICK)
    override fun zoomIn() = mouseConnection.sendCommand(MouseCommand.ZOOM_IN)
    override fun zoomOut() = mouseConnection.sendCommand(MouseCommand.ZOOM_OUT)
    override fun startActiveWindowSelection(previous: Boolean) = mouseConnection.sendCommand(
        if (previous) MouseCommand.ACTIVE_WINDOW_SWITCH_START_PREVIOUS else MouseCommand.ACTIVE_WINDOW_SWITCH_START_NEXT
    )
    override fun stepActiveWindowSelection(previous: Boolean) = mouseConnection.sendCommand(
        if (previous) MouseCommand.ACTIVE_WINDOW_SWITCH_STEP_PREVIOUS else MouseCommand.ACTIVE_WINDOW_SWITCH_STEP_NEXT
    )
    override fun commitActiveWindowSelection() = mouseConnection.sendCommand(MouseCommand.ACTIVE_WINDOW_SWITCH_COMMIT)
    override fun hankakuZenkaku() = mouseConnection.sendCommand(MouseCommand.KEY_HANKAKU_ZENKAKU)
    override fun enter() = mouseConnection.sendCommand("KEY_ENTER")
    override fun henkan() = mouseConnection.sendCommand(MouseCommand.KEY_HENKAN)
    override fun refreshF5() = mouseConnection.sendCommand(MouseCommand.KEY_F5)
    override fun home() = mouseConnection.sendCommand(MouseCommand.KEY_HOME)
    override fun end() = mouseConnection.sendCommand(MouseCommand.KEY_END)
    override fun printWholeScreen() = mouseConnection.sendCommand(MouseCommand.PRINT_SCREEN_FULL)
    override fun printActiveWindow() = mouseConnection.sendCommand(MouseCommand.PRINT_ACTIVE_WINDOW)
    override fun muteAllTouch() = mouseConnection.sendCommand(MouseCommand.AUDIO_MUTE)
    override fun mute() = mouseConnection.sendCommand(MouseCommand.AUDIO_MUTE)
    override fun previousActiveWindow() = mouseConnection.sendCommand(MouseCommand.ACTIVE_WINDOW_PREVIOUS)
    override fun nextActiveWindow() = mouseConnection.sendCommand(MouseCommand.ACTIVE_WINDOW_NEXT)
    override fun openBrowserSearch() = mouseConnection.sendCommand("OPEN_BROWSER_SEARCH")
    override fun find() = mouseConnection.sendCommand("KEY_FIND")
    override fun copy() = mouseConnection.sendCommand("COPY")
    override fun paste() = mouseConnection.sendCommand("PASTE")
    override fun undo() = mouseConnection.sendCommand("UNDO")
    override fun redo() = mouseConnection.sendCommand("REDO")
    override fun escapeKey() = mouseConnection.sendCommand("KEY_ESCAPE")
    override fun showDesktop() = mouseConnection.sendCommand("SHOW_DESKTOP")
    override fun cut() = mouseConnection.sendCommand("CUT")
    override fun selectAll() = mouseConnection.sendCommand("SELECT_ALL")
    override fun deleteKey() = mouseConnection.sendCommand("KEY_DELETE")
    override fun minimizeWindow() = mouseConnection.sendCommand("WINDOW_MINIMIZE")
    override fun maximizeWindow() = mouseConnection.sendCommand("WINDOW_MAXIMIZE")
    override fun previousTab() = mouseConnection.sendCommand("TAB_PREVIOUS")
    override fun nextTab() = mouseConnection.sendCommand("TAB_NEXT")
}

class BluetoothInputTransport(private val bluetoothHid: BluetoothHidBridge) : InputTransport {
    override fun isConnected() = bluetoothHid.isConnected()
    override fun move(dx: Int, dy: Int) { bluetoothHid.move(dx, dy) }
    override fun scroll(amount: Int) { bluetoothHid.wheel(amount) }
    override fun horizontalScroll(amount: Int) { /* current HID descriptor has no horizontal wheel */ }
    override fun leftClick() { bluetoothHid.click(HidMousePacket.LEFT) }
    override fun rightClick() { bluetoothHid.click(HidMousePacket.RIGHT) }
    override fun middleClick() { bluetoothHid.click(HidMousePacket.MIDDLE) }
    override fun leftDown() { bluetoothHid.buttonDown(HidMousePacket.LEFT) }
    override fun leftUp() { bluetoothHid.buttonUp(HidMousePacket.LEFT) }
    override fun back() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_LEFT_ARROW, HidKeyboardPacket.MOD_LEFT_ALT) }
    override fun forward() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_RIGHT_ARROW, HidKeyboardPacket.MOD_LEFT_ALT) }
    override fun doubleLeftClick() { bluetoothHid.click(HidMousePacket.LEFT); bluetoothHid.click(HidMousePacket.LEFT) }
    override fun zoomIn() {
        HidKeyboardPacket.strokeFor('+')?.let {
            bluetoothHid.pressKey(it.key, (HidKeyboardPacket.MOD_LEFT_CTRL.toInt() or it.modifiers.toInt()).toByte())
        }
    }
    override fun zoomOut() {
        HidKeyboardPacket.strokeFor('-')?.let {
            bluetoothHid.pressKey(it.key, (HidKeyboardPacket.MOD_LEFT_CTRL.toInt() or it.modifiers.toInt()).toByte())
        }
    }
    override fun startActiveWindowSelection(previous: Boolean) { bluetoothHid.altTabStep(previous) }
    override fun stepActiveWindowSelection(previous: Boolean) { bluetoothHid.altTabStep(previous) }
    override fun commitActiveWindowSelection() { bluetoothHid.releaseKeyboard() }
    override fun hankakuZenkaku() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_HANKAKU_ZENKAKU) }
    override fun enter() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_ENTER) }
    override fun henkan() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_HENKAN) }
    override fun refreshF5() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_F5) }
    override fun home() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_HOME) }
    override fun end() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_END) }
    override fun printWholeScreen() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_PRINT_SCREEN) }
    override fun printActiveWindow() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_PRINT_SCREEN, HidKeyboardPacket.MOD_LEFT_ALT) }
    override fun muteAllTouch() { bluetoothHid.testConsumerMute() }
    override fun mute() { bluetoothHid.testConsumerMute() }
    override fun previousActiveWindow() {
        bluetoothHid.pressKey(HidKeyboardPacket.KEY_TAB, (HidKeyboardPacket.MOD_LEFT_ALT.toInt() or HidKeyboardPacket.MOD_LEFT_SHIFT.toInt()).toByte())
    }
    override fun nextActiveWindow() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_TAB, HidKeyboardPacket.MOD_LEFT_ALT) }
    override fun openBrowserSearch() { bluetoothHid.pressKey(0x0F, HidKeyboardPacket.MOD_LEFT_GUI) }
    override fun find() { bluetoothHid.pressKey(0x09, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun copy() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_C, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun paste() { bluetoothHid.pressKey(0x19, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun undo() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_Z, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun redo() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_Y, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun escapeKey() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_ESCAPE) }
    override fun showDesktop() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_D, HidKeyboardPacket.MOD_LEFT_GUI) }
    override fun cut() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_X, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun selectAll() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_A, HidKeyboardPacket.MOD_LEFT_CTRL) }
    override fun deleteKey() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_DELETE) }
    override fun minimizeWindow() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_DOWN_ARROW, HidKeyboardPacket.MOD_LEFT_GUI) }
    override fun maximizeWindow() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_UP_ARROW, HidKeyboardPacket.MOD_LEFT_GUI) }
    override fun previousTab() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_TAB, (HidKeyboardPacket.MOD_LEFT_CTRL.toInt() or HidKeyboardPacket.MOD_LEFT_SHIFT.toInt()).toByte()) }
    override fun nextTab() { bluetoothHid.pressKey(HidKeyboardPacket.KEY_TAB, HidKeyboardPacket.MOD_LEFT_CTRL) }
}
