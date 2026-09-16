package com.example.diazymouse.bhid.hid

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object HidSessionUtils {
    fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    fun deviceLabel(device: BluetoothDevice?): String {
        if (device == null) return "null"
        return try {
            "${device.name ?: "(unnamed)"} [${device.address}]"
        } catch (_: SecurityException) {
            "(permission denied)"
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
