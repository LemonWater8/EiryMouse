package com.example.diazymouse.bhid.hid

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import com.example.diazymouse.bhid.logging.HidLogger
import com.example.diazymouse.bhid.report.HidConsumerPacket
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.bhid.report.HidMousePacket

class HidSessionReportSender(
    private val logger: HidLogger,
    private val proxyProvider: () -> BluetoothHidDevice?,
    private val currentDeviceProvider: () -> BluetoothDevice?,
    private val currentStateProvider: () -> Int,
    private val stateChanged: () -> Unit,
    private val hasConnectPermission: () -> Boolean
) {
    @Volatile
    private var heldMouseButtons: Byte = 0
    private var heldKeyboardModifiers: Byte = 0

    fun move(dx: Int, dy: Int): Boolean = send(heldMouseButtons, dx, dy, 0)

    fun wheel(amount: Int): Boolean = send(heldMouseButtons, 0, 0, amount)

    fun click(button: Byte): Boolean {
        val downButtons = (heldMouseButtons.toInt() or button.toInt()).toByte()
        val down = send(downButtons, 0, 0, 0)
        val up = send(heldMouseButtons, 0, 0, 0)
        return down && up
    }

    fun buttonDown(button: Byte): Boolean {
        heldMouseButtons = (heldMouseButtons.toInt() or button.toInt()).toByte()
        return send(heldMouseButtons, 0, 0, 0)
    }

    fun buttonUp(button: Byte): Boolean {
        heldMouseButtons = (heldMouseButtons.toInt() and button.toInt().inv()).toByte()
        return send(heldMouseButtons, 0, 0, 0)
    }

    fun keyPress(key: Byte, modifiers: Byte = 0): Boolean {
        val effective = (heldKeyboardModifiers.toInt() or modifiers.toInt()).toByte()
        val down = sendReport(HidKeyboardPacket.REPORT_ID, HidKeyboardPacket.encode(effective, key), "keyboard-down")
        val up = sendReport(HidKeyboardPacket.REPORT_ID, HidKeyboardPacket.encode(heldKeyboardModifiers), "keyboard-up")
        return down && up
    }

    fun holdModifiers(modifiers: Byte): Boolean {
        heldKeyboardModifiers = modifiers
        return sendReport(HidKeyboardPacket.REPORT_ID, HidKeyboardPacket.encode(heldKeyboardModifiers), "modifier-held")
    }


    /**
     * Sends one Alt+Tab navigation step while leaving Left Alt held afterwards.
     * This is required for the Windows task-switcher overlay to remain visible
     * until the four-finger gesture is released.
     */
    fun altTabStep(previous: Boolean): Boolean {
        val modifiers = if (previous) {
            (HidKeyboardPacket.MOD_LEFT_ALT.toInt() or HidKeyboardPacket.MOD_LEFT_SHIFT.toInt()).toByte()
        } else {
            HidKeyboardPacket.MOD_LEFT_ALT
        }
        val down = sendReport(
            HidKeyboardPacket.REPORT_ID,
            HidKeyboardPacket.encode(modifiers, HidKeyboardPacket.KEY_TAB),
            if (previous) "alt-shift-tab-down" else "alt-tab-down"
        )
        // Release Tab (and Shift) but deliberately keep Alt held.
        val holdAlt = sendReport(
            HidKeyboardPacket.REPORT_ID,
            HidKeyboardPacket.encode(HidKeyboardPacket.MOD_LEFT_ALT),
            "alt-held"
        )
        return down && holdAlt
    }

    fun releaseKeyboard(): Boolean {
        heldKeyboardModifiers = 0
        return sendReport(HidKeyboardPacket.REPORT_ID, HidKeyboardPacket.neutral(), "keyboard-neutral")
    }

    fun typeAsciiText(text: String): Boolean {
        if (!HidKeyboardPacket.isSupportedDirectInput(text)) return false

        var success = true
        for (ch in text) {
            val stroke = HidKeyboardPacket.strokeFor(ch) ?: continue
            success = keyPress(stroke.key, stroke.modifiers) && success
        }
        return success
    }

    fun backspace(): Boolean = keyPress(HidKeyboardPacket.KEY_BACKSPACE)

    fun consumerPress(button: Byte): Boolean {
        val down = sendReport(HidConsumerPacket.REPORT_ID, HidConsumerPacket.encode(button), "consumer-down")
        val up = sendReport(HidConsumerPacket.REPORT_ID, HidConsumerPacket.neutral(), "consumer-up")
        return down && up
    }

    fun send(buttons: Byte, dx: Int, dy: Int, wheel: Int): Boolean {
        val payload = HidMousePacket.encode(buttons, dx, dy, wheel)
        return sendReport(
            HidMousePacket.REPORT_ID,
            payload,
            "mouse buttons=${buttons.toInt() and 0xff} dx=$dx dy=$dy wheel=$wheel"
        )
    }

    fun sendReport(reportId: Int, payload: ByteArray, label: String): Boolean {
        val proxy = proxyProvider() ?: run {
            logger.log("v16.0 $label blocked: HID proxy unavailable")
            return false
        }
        val device = currentDeviceProvider() ?: run {
            logger.log("v16.0 $label blocked: no current device")
            return false
        }
        if (!hasConnectPermission()) return false

        val state = apiState(proxy, device)
        if (state != BluetoothProfile.STATE_CONNECTED) {
            logger.log("v16.0 $label blocked: API state=${HidSessionUtils.stateName(state)}")
            stateChanged()
            return false
        }

        val accepted = try {
            proxy.sendReport(device, reportId, payload)
        } catch (e: SecurityException) {
            logger.log("v16.0 $label SecurityException=${e.message}")
            false
        }
        logger.log(
            "v16.0 sendReport id=$reportId label=$label accepted=$accepted bytes=" +
                payload.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
        )
        return accepted
    }

    private fun apiState(proxy: BluetoothHidDevice, device: BluetoothDevice): Int =
        try {
            proxy.getConnectionState(device)
        } catch (_: SecurityException) {
            BluetoothProfile.STATE_DISCONNECTED
        }
}
