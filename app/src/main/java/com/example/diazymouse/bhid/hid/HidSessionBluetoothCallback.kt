package com.example.diazymouse.bhid.hid

import com.example.diazymouse.bhid.logging.HidLogger
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice

/**
 * Thin Android BluetoothHidDevice.Callback adapter.
 *
 * IMPORTANT:
 * The delegate fields deliberately have names different from the Android
 * callback methods.  Using the same names causes an unqualified call such as
 * onAppStatusChanged(...) to resolve to this override itself and recurse until
 * StackOverflowError, exactly what the 2026-09-01 device log showed.
 */
internal class HidSessionBluetoothCallback(
    private val logger: HidLogger,
    private val hasConnectPermission: () -> Boolean,
    private val stateName: (Int) -> String,
    private val deviceLabel: (BluetoothDevice?) -> String,
    private val appStatusHandler: (BluetoothDevice?, Boolean) -> Unit,
    private val connectionStateHandler: (BluetoothDevice, Int) -> Unit,
    private val getReportHandler: (BluetoothDevice, Byte, Byte, Int) -> Unit,
    private val setProtocolHandler: (BluetoothDevice, Byte) -> Unit,
    private val virtualCableUnplugHandler: (BluetoothDevice) -> Unit
) : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
        appStatusHandler(pluggedDevice, isRegistered)
    }

    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        connectionStateHandler(device, state)
    }

    override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
        getReportHandler(device, type, id, bufferSize)
    }

    override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
        setProtocolHandler(device, protocol)
    }

    override fun onVirtualCableUnplug(device: BluetoothDevice) {
        virtualCableUnplugHandler(device)
    }
}
