@file:Suppress("MissingPermission", "unused")

package com.example.diazymouse.bhid.hid

import com.example.diazymouse.bhid.logging.HidLogger
import com.example.diazymouse.bhid.report.HidMousePacket
import com.example.diazymouse.bhid.report.HidKeyboardPacket
import com.example.diazymouse.bhid.report.HidConsumerPacket
import com.example.diazymouse.bhid.report.CompositeHidDescriptor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Independent Bluetooth HID mouse session.
 *
 * v15.0 uses a bounded recovery state machine for the unstable first HID link:
 *  1) ensure HID_DEVICE proxy
 *  2) ensure registerApp callback == registered
 *  3) short settle delay
 *  4) connect
 *  5) if CONNECTING -> DISCONNECTED, retry connect once while preserving
 *     the existing HID Device registration and Bluetooth bond
 *  6) stop after the second failed connection attempt
 *
 * No infinite retry loop is used.
 */
class HidSessionManager(
    context: Context,
    private val logger: HidLogger,
    private val onStateChanged: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var registrationPending = false
    private var currentDevice: BluetoothDevice? = null
    private var currentState = BluetoothProfile.STATE_DISCONNECTED
    private var queuedConnect: BluetoothDevice? = null
    private var callbackPluggedDevice: BluetoothDevice? = null
    private var closed = false
    private val reportSender: HidSessionReportSender = HidSessionReportSender(
        logger = logger,
        proxyProvider = { hid },
        currentDeviceProvider = { currentDevice },
        currentStateProvider = { currentState },
        stateChanged = { notifyState() },
        hasConnectPermission = { hasConnectPermission() }
    )

    // UI can subscribe after the foreground service has already created the session.
    private var stateListener: (() -> Unit)? = null
    private var pairingReadyCallback: ((Boolean) -> Unit)? = null

    private enum class StableStage {
        IDLE,
        WAIT_PROXY,
        WAIT_REGISTER,
        SETTLE_BEFORE_CONNECT,
        HOST_WAIT,
        CONNECTING_FIRST,
        SETTLE_BEFORE_RECONNECT,
        CONNECTING_SECOND,
        CONNECTED,
        FAILED
    }

    private var stableStage = StableStage.IDLE
    private var stableTarget: BluetoothDevice? = null
    private var stableAttempt = 0
    private var userDisconnectRequested = false

    private val settleBeforeFirstConnectMs = 3_000L
    // Prefer the real HID topology: Windows (Host) opens the HID link.
    // Only if no host-initiated connection arrives in this window do we
    // fall back to BluetoothHidDevice.connect().
    private val hostInitiatedWaitMs = 18_000L
    private val settleBeforeSecondConnectMs = 3_000L
    private val connectionTimeoutMs = 12_000L
    private val registrationTimeoutMs = 8_000L

    private val FLOW_TOKEN = Any()

    private val sdp = BluetoothHidDeviceAppSdpSettings(
        "BHID_NEXT_v1.0",
        "Mouse + Keyboard + Consumer Control verification",
        "BHID_NEXT_v1.0",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        CompositeHidDescriptor.descriptor
    )

    internal fun hasConnectPermission(): Boolean =
        HidSessionUtils.hasBluetoothConnectPermission(appContext)

    internal fun stateName(state: Int): String = HidSessionUtils.stateName(state)

    internal fun deviceLabel(device: BluetoothDevice?): String = HidSessionUtils.deviceLabel(device)

    private val callback = HidSessionBluetoothCallback(
        logger = logger,
        hasConnectPermission = ::hasConnectPermission,
        stateName = ::stateName,
        deviceLabel = ::deviceLabel,
        appStatusHandler = ::handleAppStatusChanged,
        connectionStateHandler = ::handleConnectionStateChanged,
        getReportHandler = ::handleGetReport,
        setProtocolHandler = ::handleSetProtocol,
        virtualCableUnplugHandler = ::handleVirtualCableUnplug
    )

    internal fun handleAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
        registered = isRegistered
        registrationPending = false
        callbackPluggedDevice = if (isRegistered) pluggedDevice else null

        logger.log(
            "v15.0 CALLBACK appStatus registered=$isRegistered " +
                "pluggedDevice=${deviceLabel(pluggedDevice)} stage=$stableStage"
        )

        if (!isRegistered) {
            pairingReadyCallback?.let { callback ->
                pairingReadyCallback = null
                callback(false)
            }
            currentDevice = null
            currentState = BluetoothProfile.STATE_DISCONNECTED

            if (stableStage != StableStage.IDLE && stableStage != StableStage.FAILED) {
                failStableFlow("registration removed outside recovery")
            }
        } else {
            pairingReadyCallback?.let { callback ->
                pairingReadyCallback = null
                callback(true)
            }
            when (stableStage) {
                StableStage.WAIT_REGISTER -> {
                    val target = stableTarget
                    if (target != null) {
                        logger.log("v15.0 registration confirmed; entering host-first wait")
                        beginHostWait(target)
                    } else {
                        logger.log("v15.0 registration confirmed but target missing")
                        failStableFlow("registered with no target")
                    }
                }
                else -> {
                    val target = queuedConnect
                    if (target != null) {
                        queuedConnect = null
                        logger.log("v15.0 normal registration confirmed; connecting ${deviceLabel(target)}")
                        connectRaw(target)
                    }
                }
            }
        }
        notifyState()
    }

    internal fun handleConnectionStateChanged(device: BluetoothDevice, state: Int) {
        val previous = currentState
        currentState = state
        currentDevice = when (state) {
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTING -> device
            else -> null
        }

        logger.log(
            "v15.0 CALLBACK connection device=${deviceLabel(device)} " +
                "state=${stateName(state)} previous=${stateName(previous)} stage=$stableStage attempt=$stableAttempt"
        )

        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                handler.removeCallbacksAndMessages(FLOW_TOKEN)
                stableStage = StableStage.CONNECTED
                stableAttempt = maxOf(stableAttempt, 1)
                userDisconnectRequested = false
                logger.log("v15.0 STABLE SUCCESS attempt=$stableAttempt")
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                if (userDisconnectRequested) {
                    finishStableFlow()
                } else if (shouldRetryAfterDisconnect(previous, stableStage)) {
                    logger.log(
                        "v15.0 STABLE unexpected disconnect previous=${stateName(previous)} " +
                            "stage=$stableStage target=${deviceLabel(stableTarget)}; retrying"
                    )
                    beginConnectionRecovery()
                } else if (
                    stableStage == StableStage.CONNECTING_SECOND &&
                    previous == BluetoothProfile.STATE_CONNECTING
                ) {
                    failStableFlow("second connection attempt disconnected")
                }
            }
        }
        notifyState()
    }

    internal fun handleGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
        logger.log(
            "v15.0 CALLBACK getReport device=${deviceLabel(device)} type=$type id=$id size=$bufferSize"
        )
        if (!hasConnectPermission()) {
            logger.log("v15.0 getReport blocked: BLUETOOTH_CONNECT missing")
            return
        }

        val proxy = hid ?: return
        if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
            val neutral = when (id.toInt()) {
                HidMousePacket.REPORT_ID -> HidMousePacket.encode()
                HidKeyboardPacket.REPORT_ID -> HidKeyboardPacket.neutral()
                HidConsumerPacket.REPORT_ID -> HidConsumerPacket.neutral()
                else -> null
            }
            if (neutral != null) {
                val accepted = proxy.replyReport(device, type, id, neutral)
                logger.log("v16.0 replyReport neutral id=${id.toInt()} accepted=$accepted")
            } else {
                val accepted = proxy.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                logger.log("v16.0 reportError invalidReport accepted=$accepted")
            }
        } else {
            val accepted = proxy.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            logger.log("v16.0 reportError unsupported type=$type accepted=$accepted")
        }
    }

    internal fun handleSetProtocol(device: BluetoothDevice, protocol: Byte) {
        logger.log("v15.0 CALLBACK setProtocol device=${deviceLabel(device)} protocol=$protocol")
    }

    internal fun handleVirtualCableUnplug(device: BluetoothDevice) {
        logger.log("v15.0 CALLBACK virtualCableUnplug device=${deviceLabel(device)}")
        currentDevice = null
        currentState = BluetoothProfile.STATE_DISCONNECTED
        failStableFlow("virtual cable unplug")
        notifyState()
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE || proxy !is BluetoothHidDevice) return
            hid = proxy
            logger.log("v15.0 HID_DEVICE profile acquired stage=$stableStage")

            when (stableStage) {
                StableStage.WAIT_PROXY -> ensureRegisteredForStableFlow()
                else -> {
                    val target = queuedConnect
                    if (target != null && !registered && !registrationPending) registerApplication()
                }
            }
            notifyState()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            logger.log("v15.0 HID_DEVICE profile disconnected")
            hid = null
            registered = false
            registrationPending = false
            currentDevice = null
            currentState = BluetoothProfile.STATE_DISCONNECTED
            queuedConnect = null
            callbackPluggedDevice = null
            failStableFlow("HID proxy disconnected")
            notifyState()
        }
    }

    init {
        val accepted = adapter?.getProfileProxy(
            appContext,
            profileListener,
            BluetoothProfile.HID_DEVICE
        ) ?: false
        logger.log("v15.0 getProfileProxy accepted=$accepted")
    }

    fun setStateListener(listener: (() -> Unit)?) {
        stateListener = listener
        notifyState()
    }

    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return try {
            adapter?.bondedDevices?.sortedBy { safeName(it) } ?: emptyList()
        } catch (e: SecurityException) {
            logger.log("v15.0 bondedDevices SecurityException=${e.message}")
            emptyList()
        }
    }

    fun registerApplication(): Boolean {
        val proxy = hid ?: run {
            logger.log("v15.0 register deferred: HID proxy unavailable")
            return false
        }
        if (!hasConnectPermission()) {
            logger.log("v15.0 register blocked: BLUETOOTH_CONNECT missing")
            return false
        }
        if (registered) {
            logger.log("v15.0 register skipped: already registered")
            return true
        }
        if (registrationPending) {
            logger.log("v15.0 register skipped: registration already pending")
            return true
        }

        registrationPending = true
        val accepted = try {
            proxy.registerApp(sdp, null, null, callbackExecutor, callback)
        } catch (e: SecurityException) {
            logger.log("v15.0 register SecurityException=${e.message}")
            false
        }
        if (!accepted) registrationPending = false
        logger.log("v15.0 registerApp requestAccepted=$accepted")
        notifyState()
        return accepted
    }

    fun prepareForPairing(onReady: (Boolean) -> Unit): Boolean {
        if (!hasConnectPermission()) {
            logger.log("v15.0 pairing preparation blocked: BLUETOOTH_CONNECT missing")
            onReady(false)
            return false
        }
        if (hid == null) {
            logger.log("v15.0 pairing preparation blocked: HID proxy unavailable")
            onReady(false)
            return false
        }
        if (registered) {
            logger.log("v15.0 pairing preparation ready: HID already registered")
            onReady(true)
            return true
        }

        pairingReadyCallback = onReady
        val accepted = registerApplication()
        if (!accepted) {
            pairingReadyCallback = null
            onReady(false)
        } else {
            logger.log("v15.0 pairing preparation waiting for registration callback")
        }
        return accepted
    }

    /**
     * v15.0 preferred first-pairing path.
     *
     * HID Device is already registered before the Android pairing UI is opened.
     * After BOND_BONDED, wait for Windows (HID Host) to open the HID connection.
     * Only after a bounded grace period do we issue an active connect fallback.
     */
    fun requestHostFirst(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission()) {
            logger.log("v15.0 HOST_FIRST blocked: BLUETOOTH_CONNECT missing")
            return false
        }

        val proxy = hid
        if (proxy != null) {
            val state = apiState(proxy, device)
            if (state == BluetoothProfile.STATE_CONNECTED) {
                currentDevice = device
                currentState = state
                stableTarget = device
                stableStage = StableStage.CONNECTED
                stableAttempt = maxOf(stableAttempt, 1)
                logger.log("v15.0 HOST_FIRST already CONNECTED")
                notifyState()
                return true
            }
        }

        if (stableStage !in setOf(
                StableStage.IDLE,
                StableStage.FAILED,
                StableStage.CONNECTED,
                StableStage.HOST_WAIT
            )
        ) {
            logger.log("v15.0 HOST_FIRST ignored: flow active stage=$stableStage")
            return true
        }

        resetFlowTimers()
        stableTarget = device
        stableAttempt = 0
        userDisconnectRequested = false
        queuedConnect = null

        if (hid == null) {
            // Extremely uncommon because prepareForPairing() runs first.
            stableStage = StableStage.WAIT_PROXY
            logger.log("v15.0 HOST_FIRST waiting for HID proxy")
            notifyState()
            return true
        }

        if (!registered) {
            stableStage = StableStage.WAIT_REGISTER
            val accepted = registerApplication()
            logger.log("v15.0 HOST_FIRST register request accepted=$accepted")
            if (!accepted) {
                failStableFlow("host-first register rejected")
                return false
            }
            scheduleTimeout("host-first registration timeout") {
                stableStage == StableStage.WAIT_REGISTER && !registered
            }
            notifyState()
            return true
        }

        beginHostWait(device)
        return true
    }

    fun requestStableConnect(device: BluetoothDevice): Boolean {
        if (!hasConnectPermission()) {
            logger.log("v15.0 STABLE blocked: BLUETOOTH_CONNECT missing")
            return false
        }

        val currentApiState = hid?.let { apiState(it, device) } ?: BluetoothProfile.STATE_DISCONNECTED
        if (currentApiState == BluetoothProfile.STATE_CONNECTED) {
            currentDevice = device
            currentState = currentApiState
            stableTarget = device
            stableStage = StableStage.CONNECTED
            logger.log("v15.0 STABLE request: already CONNECTED")
            notifyState()
            return true
        }

        if (stableStage !in setOf(StableStage.IDLE, StableStage.FAILED, StableStage.CONNECTED)) {
            logger.log("v15.0 STABLE request ignored: flow already active stage=$stableStage")
            return true
        }

        resetFlowTimers()
        stableTarget = device
        stableAttempt = 0
        userDisconnectRequested = false
        queuedConnect = null

        logger.log(
            "v15.0 STABLE requested target=${deviceLabel(device)} " +
                "profileReady=${hid != null} registered=$registered registrationPending=$registrationPending"
        )

        if (hid == null) {
            stableStage = StableStage.WAIT_PROXY
            logger.log("v15.0 STABLE waiting for HID proxy")
            notifyState()
            return true
        }

        ensureRegisteredForStableFlow()
        notifyState()
        return true
    }

    fun disconnect(): Boolean {
        userDisconnectRequested = true
        resetFlowTimers()
        stableStage = StableStage.IDLE
        stableTarget = null

        val proxy = hid ?: return false
        val device = currentDevice ?: return false
        if (!hasConnectPermission()) return false
        val accepted = try {
            proxy.disconnect(device)
        } catch (e: SecurityException) {
            logger.log("v15.0 disconnect SecurityException=${e.message}")
            false
        }
        logger.log("v15.0 disconnect requestAccepted=$accepted device=${deviceLabel(device)}")
        notifyState()
        return accepted
    }

    fun unregisterApplication(): Boolean {
        resetFlowTimers()
        stableStage = StableStage.IDLE
        stableTarget = null
        queuedConnect = null

        if (!registered && !registrationPending) {
            logger.log("v15.0 unregister skipped: not registered")
            return true
        }
        val proxy = hid ?: return false
        if (!hasConnectPermission()) return false
        val accepted = try {
            proxy.unregisterApp()
        } catch (e: SecurityException) {
            logger.log("v15.0 unregister SecurityException=${e.message}")
            false
        }
        logger.log("v15.0 unregisterApp requestAccepted=$accepted")
        notifyState()
        return accepted
    }

    fun move(dx: Int, dy: Int): Boolean = reportSender.move(dx, dy)
    fun wheel(amount: Int): Boolean = reportSender.wheel(amount)

    fun click(button: Byte): Boolean = reportSender.click(button)
    fun buttonDown(button: Byte): Boolean = reportSender.buttonDown(button)
    fun buttonUp(button: Byte): Boolean = reportSender.buttonUp(button)

    fun keyPress(key: Byte, modifiers: Byte = 0): Boolean = reportSender.keyPress(key, modifiers)
    fun holdModifiers(modifiers: Byte): Boolean = reportSender.holdModifiers(modifiers)
    fun altTabStep(previous: Boolean): Boolean = reportSender.altTabStep(previous)
    fun releaseKeyboard(): Boolean = reportSender.releaseKeyboard()

    fun typeAsciiText(text: String): Boolean = reportSender.typeAsciiText(text)

    fun backspace(): Boolean = reportSender.backspace()

    fun consumerPress(button: Byte): Boolean = reportSender.consumerPress(button)

    fun snapshot(): Snapshot {
        val proxy = hid
        val device = currentDevice ?: stableTarget
        val state = if (proxy != null && device != null && hasConnectPermission()) {
            apiState(proxy, device)
        } else {
            currentState
        }

        return Snapshot(
            profileReady = proxy != null,
            registered = registered,
            registrationPending = registrationPending,
            device = currentDevice,
            callbackPluggedDevice = callbackPluggedDevice,
            callbackState = currentState,
            apiState = state,
            initialFlowActive = stableStage !in setOf(StableStage.IDLE, StableStage.CONNECTED, StableStage.FAILED),
            initialRetryUsed = stableAttempt >= 2,
            stableStage = stableStage.name,
            stableAttempt = stableAttempt
        )
    }

    fun close() {
        if (closed) return
        closed = true
        pairingReadyCallback = null
        stateListener = null
        resetFlowTimers()
        logger.log("v15.0 session close requested")
        if (registered || registrationPending) {
            try { hid?.unregisterApp() } catch (_: Exception) { }
        }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        logger.log("v15.0 session closed")
    }

    private fun ensureRegisteredForStableFlow() {
        val target = stableTarget ?: return failStableFlow("stable target missing")
        if (hid == null) {
            stableStage = StableStage.WAIT_PROXY
            return
        }

        if (registered) {
            logger.log("v15.0 STABLE HID already registered; scheduling first connect")
            scheduleFirstConnect()
            return
        }

        stableStage = StableStage.WAIT_REGISTER
        if (!registrationPending) {
            val accepted = registerApplication()
            if (!accepted) {
                failStableFlow("registerApp request rejected")
                return
            }
        }

        scheduleTimeout("first registration timeout") {
            stableStage == StableStage.WAIT_REGISTER && !registered
        }
        logger.log("v15.0 STABLE waiting for registration callback target=${deviceLabel(target)}")
    }

    private fun beginHostWait(device: BluetoothDevice) {
        resetFlowTimers()
        stableTarget = device

        val proxy = hid ?: return failStableFlow("HID proxy missing before host wait")
        if (!registered) return failStableFlow("registration lost before host wait")

        val state = apiState(proxy, device)
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                currentDevice = device
                currentState = state
                stableStage = StableStage.CONNECTED
                stableAttempt = maxOf(stableAttempt, 1)
                logger.log("v15.0 HOST_FIRST OS already CONNECTED")
                notifyState()
                return
            }
            BluetoothProfile.STATE_CONNECTING -> {
                // Host is already opening the channel. Do not interfere.
                currentDevice = device
                currentState = state
            }
        }

        stableStage = StableStage.HOST_WAIT
        logger.log(
            "v15.0 HOST_FIRST waiting ${hostInitiatedWaitMs}ms for Windows host " +
                "device=${deviceLabel(device)} state=${stateName(state)}"
        )

        postFlow(hostInitiatedWaitMs) {
            if (stableStage != StableStage.HOST_WAIT) return@postFlow

            val nowProxy = hid ?: return@postFlow failStableFlow("proxy lost during host wait")
            val now = apiState(nowProxy, device)
            logger.log("v15.0 HOST_FIRST grace expired apiState=${stateName(now)}")

            when (now) {
                BluetoothProfile.STATE_CONNECTED -> {
                    currentDevice = device
                    currentState = now
                    stableStage = StableStage.CONNECTED
                    stableAttempt = maxOf(stableAttempt, 1)
                    logger.log("v15.0 HOST_FIRST SUCCESS during grace")
                    notifyState()
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    // Give the OS-owned connection some more time rather than starting
                    // a competing connect() request.
                    stableAttempt = 1
                    stableStage = StableStage.CONNECTING_FIRST
                    logger.log("v15.0 HOST_FIRST host still CONNECTING; monitoring without new connect")
                    scheduleConnectionTimeout(device, first = true)
                    notifyState()
                }

                else -> {
                    logger.log("v15.0 HOST_FIRST no host open; falling back to active connect")
                    scheduleFirstConnect()
                }
            }
        }
        notifyState()
    }

    private fun scheduleFirstConnect() {
        val target = stableTarget ?: return failStableFlow("target missing before first connect")
        resetFlowTimers()
        stableStage = StableStage.SETTLE_BEFORE_CONNECT
        logger.log("v15.0 STABLE settle ${settleBeforeFirstConnectMs}ms before first connect")
        postFlow(settleBeforeFirstConnectMs) {
            if (stableStage != StableStage.SETTLE_BEFORE_CONNECT) return@postFlow
            issueStableConnect(target, first = true)
        }
    }

    private fun issueStableConnect(device: BluetoothDevice, first: Boolean) {
        val proxy = hid ?: return failStableFlow("HID proxy missing before connect")
        if (!registered) return failStableFlow("registration lost before connect")

        val state = apiState(proxy, device)
        logger.log("v15.0 STABLE connect preflight state=${stateName(state)} first=$first")
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                currentDevice = device
                currentState = state
                stableStage = StableStage.CONNECTED
                logger.log("v15.0 STABLE OS already CONNECTED")
                notifyState()
                return
            }
            BluetoothProfile.STATE_CONNECTING -> {
                stableAttempt = if (first) 1 else 2
                stableStage = if (first) StableStage.CONNECTING_FIRST else StableStage.CONNECTING_SECOND
                scheduleConnectionTimeout(device, first)
                notifyState()
                return
            }
            BluetoothProfile.STATE_DISCONNECTING -> {
                failStableFlow("API still DISCONNECTING before connect")
                return
            }
        }

        stableAttempt = if (first) 1 else 2
        stableStage = if (first) StableStage.CONNECTING_FIRST else StableStage.CONNECTING_SECOND
        val accepted = connectRaw(device)
        logger.log("v15.0 STABLE connect issued attempt=$stableAttempt accepted=$accepted")
        if (!accepted) {
            if (first) beginConnectionRecovery() else failStableFlow("second connect request rejected")
            return
        }
        scheduleConnectionTimeout(device, first)
        notifyState()
    }

    private fun scheduleConnectionTimeout(device: BluetoothDevice, first: Boolean) {
        postFlow(connectionTimeoutMs) {
            val expected = if (first) StableStage.CONNECTING_FIRST else StableStage.CONNECTING_SECOND
            if (stableStage != expected) return@postFlow
            val proxy = hid ?: return@postFlow failStableFlow("proxy lost during connection timeout")
            val state = apiState(proxy, device)
            logger.log("v15.0 STABLE connection timeout attempt=${if (first) 1 else 2} apiState=${stateName(state)}")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                currentDevice = device
                currentState = state
                stableStage = StableStage.CONNECTED
                notifyState()
            } else if (first) {
                beginConnectionRecovery()
            } else {
                failStableFlow("second connection timeout")
            }
        }
    }

    private fun beginConnectionRecovery() {
        if (stableStage == StableStage.SETTLE_BEFORE_RECONNECT ||
            stableStage == StableStage.CONNECTING_SECOND
        ) return

        resetFlowTimers()
        val target = stableTarget ?: return failStableFlow("target missing for recovery")
        if (hid == null || !registered) return failStableFlow("HID registration missing for recovery")
        logger.log("v15.0 RECOVERY start: preserve HID registration and retry connect once")

        stableStage = StableStage.SETTLE_BEFORE_RECONNECT
        logger.log("v15.0 RECOVERY settle ${settleBeforeSecondConnectMs}ms before reconnect")
        postFlow(settleBeforeSecondConnectMs) {
            if (stableStage != StableStage.SETTLE_BEFORE_RECONNECT) return@postFlow
            issueStableConnect(target, first = false)
        }
        notifyState()
    }

    private fun connectRaw(device: BluetoothDevice): Boolean {
        val proxy = hid ?: run {
            logger.log("v15.0 connect blocked: HID proxy unavailable")
            return false
        }
        if (!hasConnectPermission()) return false
        if (!registered) {
            logger.log("v15.0 connect blocked: not registered")
            return false
        }

        val state = apiState(proxy, device)
        logger.log("v15.0 connect preflight device=${deviceLabel(device)} apiState=${stateName(state)}")
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> return true
            BluetoothProfile.STATE_CONNECTING -> return true
            BluetoothProfile.STATE_DISCONNECTING -> return false
        }

        val accepted = try {
            proxy.connect(device)
        } catch (e: SecurityException) {
            logger.log("v15.0 connect SecurityException=${e.message}")
            false
        }
        logger.log("v15.0 connect requestAccepted=$accepted device=${deviceLabel(device)}")
        return accepted
    }

    private fun postFlow(delayMs: Long, block: () -> Unit) {
        handler.postAtTime(block, FLOW_TOKEN, SystemClock.uptimeMillis() + delayMs)
    }

    private fun scheduleTimeout(reason: String, stillWaiting: () -> Boolean) {
        postFlow(registrationTimeoutMs) {
            if (stillWaiting()) failStableFlow(reason)
        }
    }

    private fun resetFlowTimers() {
        handler.removeCallbacksAndMessages(FLOW_TOKEN)
    }

    private fun failStableFlow(reason: String) {
        resetFlowTimers()
        if (stableStage != StableStage.IDLE) {
            logger.log("v15.0 STABLE FAILED stage=$stableStage attempt=$stableAttempt reason=$reason")
        }
        stableStage = StableStage.FAILED
        notifyState()
    }

    private fun finishStableFlow() {
        resetFlowTimers()
        logger.log("v15.0 STABLE finished reason=user disconnect")
        stableStage = StableStage.IDLE
        stableTarget = null
        stableAttempt = 0
        notifyState()
    }

    private fun shouldRetryAfterDisconnect(previousState: Int, stage: StableStage): Boolean {
        if (stage == StableStage.IDLE || stage == StableStage.FAILED) return false
        return previousState == BluetoothProfile.STATE_CONNECTED ||
            previousState == BluetoothProfile.STATE_CONNECTING ||
            previousState == BluetoothProfile.STATE_DISCONNECTING
    }

    private fun notifyState() {
        onStateChanged()
        stateListener?.invoke()
    }

    private fun apiState(proxy: BluetoothHidDevice, device: BluetoothDevice): Int =
        try {
            proxy.getConnectionState(device)
        } catch (_: SecurityException) {
            BluetoothProfile.STATE_DISCONNECTED
        }

    private fun safeName(device: BluetoothDevice): String =
        try { device.name ?: "(unnamed)" }
        catch (_: SecurityException) { "(permission denied)" }

    data class Snapshot(
        val profileReady: Boolean,
        val registered: Boolean,
        val registrationPending: Boolean,
        val device: BluetoothDevice?,
        val callbackPluggedDevice: BluetoothDevice?,
        val callbackState: Int,
        val apiState: Int,
        val initialFlowActive: Boolean,
        val initialRetryUsed: Boolean,
        val stableStage: String,
        val stableAttempt: Int
    )

    fun labelOf(device: BluetoothDevice?): String = deviceLabel(device)

    companion object {
        fun stateName(state: Int): String = HidSessionUtils.stateName(state)
    }
}

