package com.example.diazymouse.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.diazymouse.bhid.hid.HidSessionManager
import com.example.diazymouse.bhid.hid.HidSessionService
import com.example.diazymouse.bhid.report.HidConsumerPacket
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.bhid.report.HidMousePacket

/**
 * Thin integration layer between the existing DiazyMouse UI and the verified
 * Bluetooth HID session implementation for DiazyMouse.
 */
class BluetoothHidBridge(
    private val activity: ComponentActivity,
    private val onStateChanged: () -> Unit = {}
) {
    private var service: HidSessionService? = null
    private var bound = false
    @Volatile
    private var keyboardEnabled = false
    @Volatile
    private var prepareRequested = false
    @Volatile
    private var autoConnectRequested = false
    @Volatile
    private var bluetoothConnectStartedAtMs: Long? = null
    @Volatile
    private var bluetoothConnectElapsedMs: Long = 0L

    /**
     * One-press Bluetooth Auto flow.
     *
     * IDLE          : no Auto request yet
     * PREPARING     : permission/service/HID registration is being prepared
     * WAITING_FOR_PC: first setup; Windows must pair/connect from the PC side
     * CONNECTING    : a known/bonded PC is being connected automatically
     * CONNECTED     : real HID connection established
     * FAILED        : automatic request could not be started
     */
    enum class AutoConnectPhase {
        IDLE,
        PREPARING,
        WAITING_FOR_PC,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    @Volatile
    private var autoConnectPhase = AutoConnectPhase.IDLE

    // EiryMouse connection-state presentation.  Fault stays latched until the
    // user explicitly disconnects or starts a new connection attempt.
    @Volatile
    private var connectionFaultLatched = false
    @Volatile
    private var lastObservedConnected = false
    @Volatile
    private var intentionalDisconnect = false

    private val autoPrefs by lazy {
        activity.getSharedPreferences("bluetooth_auto_host", Context.MODE_PRIVATE)
    }

    private fun savedHostAddress(): String? =
        autoPrefs.getString("last_host_address", null)

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? =
        if (!hasBluetoothConnectPermission()) {
            null
        } else {
            try {
                device.name
            } catch (_: SecurityException) {
                null
            }
        }

    private fun saveConnectedHost(device: BluetoothDevice?) {
        if (device == null) return
        val address = runCatching { device.address }.getOrNull() ?: return
        val name = safeDeviceName(device)
        autoPrefs.edit()
            .putString("last_host_address", address)
            .putString("last_host_name", name)
            .apply()
    }

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                ensureStarted()
                if (prepareRequested) service?.session?.registerApplication()
                continuePendingAutoConnect()
            }
            onStateChanged()
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? HidSessionService.LocalBinder)?.service()
            bound = service != null
            service?.session?.setStateListener {
                continuePendingAutoConnect()
                observeConnectionTransition()
                onStateChanged()
            }
            if (prepareRequested && service?.session?.snapshot()?.registered != true) {
                service?.session?.registerApplication()
            }
            continuePendingAutoConnect()
            onStateChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.session?.setStateListener(null)
            service = null
            bound = false
            observeConnectionTransition()
            onStateChanged()
        }
    }

    private fun ensureStarted() {
        if (bound || service != null) return
        val intent = Intent(activity, HidSessionService::class.java)
        runCatching {
            ContextCompat.startForegroundService(activity, intent)
            activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }


    fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions += Manifest.permission.BLUETOOTH_SCAN
            }
        } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            ensureStarted()
            onStateChanged()
        }
    }

    fun registerApplication(): Boolean = service?.session?.registerApplication() ?: false

    /**
     * First-stage PC connection preparation. This intentionally stops after
     * Bluetooth HID registration; it never starts the host connection itself.
     * The UI can therefore expose an explicit None -> Do -> Connect flow.
     */
    fun preparePcConnection(): Boolean {
        prepareRequested = true

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions += Manifest.permission.BLUETOOTH_SCAN
            }
        } else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
            return true
        }

        ensureStarted()
        val session = service?.session ?: return true
        if (!session.snapshot().registered) session.registerApplication()
        onStateChanged()
        return true
    }


    /**
     * One-press Bluetooth AutoConnect.
     *
     * Known host:
     *   prepare HID -> automatically request the saved/bonded PC -> OK
     *
     * First setup:
     *   prepare HID -> show PC-side pairing instruction -> Windows pairs/connects
     *   -> remember that host -> OK
     */
    fun requestAutoConnect(): Boolean {
        connectionFaultLatched = false
        intentionalDisconnect = false
        if (isConnected()) {
            service?.session?.snapshot()?.device?.let(::saveConnectedHost)
            autoConnectRequested = false
            autoConnectPhase = AutoConnectPhase.CONNECTED
            onStateChanged()
            return true
        }

        if (bluetoothConnectStartedAtMs == null) {
            bluetoothConnectStartedAtMs = System.currentTimeMillis()
        }

        autoConnectRequested = true
        autoConnectPhase = AutoConnectPhase.PREPARING
        preparePcConnection()
        continuePendingAutoConnect()
        onStateChanged()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun selectKnownHost(devices: List<BluetoothDevice>): BluetoothDevice? {
        val saved = savedHostAddress()
        if (saved != null) {
            devices.firstOrNull { device ->
                runCatching { device.address == saved }.getOrDefault(false)
            }?.let { return it }
        }

        // If the app has no own history yet but Android already remembers a
        // paired computer, treat that as reusable pairing information.
        return devices.firstOrNull { device ->
            if (!hasBluetoothConnectPermission()) return@firstOrNull false
            try {
                device.bluetoothClass?.majorDeviceClass ==
                    android.bluetooth.BluetoothClass.Device.Major.COMPUTER
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private fun continuePendingAutoConnect() {
        val session = service?.session

        if (isConnected()) {
            session?.snapshot()?.device?.let(::saveConnectedHost)
            autoConnectRequested = false
            autoConnectPhase = AutoConnectPhase.CONNECTED
            onStateChanged()
            return
        }

        if (!autoConnectRequested) return
        val snap = session?.snapshot() ?: return

        if (!snap.profileReady || !snap.registered) {
            autoConnectPhase = AutoConnectPhase.PREPARING
            onStateChanged()
            return
        }

        if (autoConnectPhase == AutoConnectPhase.CONNECTING) {
            // requestStableConnect() is asynchronous. Wait for the real HID
            // callback instead of repeatedly issuing connect requests.
            onStateChanged()
            return
        }

        val devices = runCatching { session.bondedDevices() }.getOrDefault(emptyList())
        val target = selectKnownHost(devices)

        if (target == null) {
            // No reusable PC information exists. Keep HID registered and let
            // Windows perform the first pairing/connection from the PC side.
            autoConnectPhase = AutoConnectPhase.WAITING_FOR_PC
            onStateChanged()
            return
        }

        autoConnectPhase = AutoConnectPhase.CONNECTING
        val accepted = session.requestStableConnect(target)
        if (!accepted) {
            autoConnectRequested = false
            autoConnectPhase = AutoConnectPhase.FAILED
            connectionFaultLatched = true
        }
        onStateChanged()
    }

    fun isPcConnectionPrepared(): Boolean {
        val snap = service?.session?.snapshot() ?: return false
        return snap.profileReady && snap.registered
    }

    fun isAutoConnectPending(): Boolean =
        autoConnectPhase == AutoConnectPhase.PREPARING ||
            autoConnectPhase == AutoConnectPhase.CONNECTING ||
            autoConnectPhase == AutoConnectPhase.WAITING_FOR_PC

    fun autoConnectPhase(): AutoConnectPhase {
        if (isConnected()) {
            service?.session?.snapshot()?.device?.let(::saveConnectedHost)
            return AutoConnectPhase.CONNECTED
        }
        return autoConnectPhase
    }

    fun bondedDevices(): List<BluetoothDevice> = service?.session?.bondedDevices().orEmpty()

    fun connect(device: BluetoothDevice): Boolean {
        preparePcConnection()
        if (bluetoothConnectStartedAtMs == null) {
            bluetoothConnectStartedAtMs = System.currentTimeMillis()
        }
        val result = service?.session?.requestStableConnect(device) ?: false
        if (result && isConnected()) {
            bluetoothConnectElapsedMs = bluetoothConnectStartedAtMs?.let {
                System.currentTimeMillis() - it
            } ?: 0L
            bluetoothConnectStartedAtMs = null
        }
        onStateChanged()
        return result
    }

    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        activity.startActivity(intent)
    }

    fun connectionDurationMs(): Long {
        if (isConnected()) {
            val startedAt = bluetoothConnectStartedAtMs
            if (startedAt != null) {
                bluetoothConnectElapsedMs = System.currentTimeMillis() - startedAt
            }
            return bluetoothConnectElapsedMs
        }
        return bluetoothConnectElapsedMs
    }

    fun disconnect(): Boolean {
        // Explicit user/app disconnect must not be presented as an error.
        intentionalDisconnect = true
        connectionFaultLatched = false
        service?.session?.buttonUp(HidMousePacket.LEFT)
        autoConnectRequested = false
        autoConnectPhase = AutoConnectPhase.IDLE
        val result = service?.session?.disconnect() ?: false
        observeConnectionTransition()
        onStateChanged()
        return result
    }

    private fun observeConnectionTransition() {
        val nowConnected = isConnected()
        if (nowConnected) {
            connectionFaultLatched = false
            intentionalDisconnect = false
        } else if (lastObservedConnected) {
            if (!intentionalDisconnect) connectionFaultLatched = true
            intentionalDisconnect = false
        }
        if (autoConnectPhase == AutoConnectPhase.FAILED) {
            connectionFaultLatched = true
        }
        lastObservedConnected = nowConnected
    }

    fun hasConnectionFault(): Boolean = connectionFaultLatched


    fun isConnected(): Boolean =
        service?.session?.snapshot()?.apiState == BluetoothProfile.STATE_CONNECTED

    fun connectedTargetName(): String {
        val device = service?.session?.snapshot()?.device
        return device?.let(::safeDeviceName)
            ?: autoPrefs.getString("last_host_name", null)
            ?: "Unknown"
    }

    fun unregisterApplication(): Boolean = service?.session?.unregisterApplication() ?: false

    fun move(dx: Int, dy: Int): Boolean = service?.session?.move(dx, dy) ?: false

    fun wheel(amount: Int): Boolean = service?.session?.wheel(amount) ?: false

    fun click(button: Byte): Boolean = service?.session?.click(button) ?: false

    fun buttonDown(button: Byte): Boolean = service?.session?.buttonDown(button) ?: false

    fun buttonUp(button: Byte): Boolean = service?.session?.buttonUp(button) ?: false

    fun testMove(): Boolean = move(40, 0)

    fun testLeftClick(): Boolean = click(HidMousePacket.LEFT)

    fun isKeyboardEnabled(): Boolean = keyboardEnabled

    fun setKeyboardEnabled(enabled: Boolean): Boolean {
        keyboardEnabled = enabled
        return keyboardEnabled
    }

    fun toggleKeyboardEnabled(): Boolean {
        keyboardEnabled = !keyboardEnabled
        return keyboardEnabled
    }

    fun typeAsciiText(text: String): Boolean {
        if (!keyboardEnabled) return false
        return service?.session?.typeAsciiText(text) ?: false
    }

    fun pressKey(key: Byte, modifiers: Byte = 0): Boolean =
        service?.session?.keyPress(key, modifiers) ?: false

    fun holdModifiers(modifiers: Byte): Boolean =
        service?.session?.holdModifiers(modifiers) ?: false

    fun altTabStep(previous: Boolean): Boolean =
        service?.session?.altTabStep(previous) ?: false

    fun releaseKeyboard(): Boolean =
        service?.session?.releaseKeyboard() ?: false

    fun backspace(): Boolean = service?.session?.backspace() ?: false

    fun pressConsumer(button: Byte): Boolean =
        service?.session?.consumerPress(button) ?: false

    fun testKeyboardText(): Boolean = if (keyboardEnabled) typeAsciiText("HELLO") else false

    fun testConsumerVolumeUp(): Boolean = pressConsumer(HidConsumerPacket.VOLUME_UP)

    fun testConsumerMute(): Boolean = pressConsumer(HidConsumerPacket.MUTE)

    fun testBackspace(): Boolean = backspace()

    fun snapshotText(): String {
        val s = service ?: return if (bound) "HID service preparing..." else "HID service unavailable"
        val snap = s.session.snapshot()
        return buildString {
            append("Registered: ").append(snap.registered)
            append("\nState: ").append(HidSessionManager.stateName(snap.apiState))
            append("\nDevice: ").append(s.session.labelOf(snap.device))
        }
    }

    fun release() {
        service?.session?.setStateListener(null)
        if (bound) {
            runCatching { activity.unbindService(connection) }
        }
        bound = false
        service = null
    }
}

