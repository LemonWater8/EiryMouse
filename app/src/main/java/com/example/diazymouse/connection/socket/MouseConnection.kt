package com.example.diazymouse.connection.socket

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.ArrayDeque

class MouseConnection {

    private enum class ConnectionState {
        IDLE,
        ACTIVE,
        DISCONNECTING,
        EXITING,
        STOPPED
    }

    private val lock = Object()

    @Volatile
    private var running = false

    @Volatile
    private var connectionState = ConnectionState.IDLE

    private var worker: Thread? = null

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    @Volatile
    private var connectedPcName: String = "Not connected"

    @Volatile
    private var connectedAtMillis: Long = 0L

    @Volatile
    private var adbConnectStartedAtMs: Long? = null

    @Volatile
    private var adbConnectElapsedMs: Long = 0L

    @Volatile
    private var stateListener: (() -> Unit)? = null

    @Volatile
    private var exitRequestLatch: java.util.concurrent.CountDownLatch? = null

    fun currentPcName(): String = connectedPcName

    fun connectionTimeText(): String {
        if (connectedAtMillis == 0L) return "Not connected"
        val date = java.util.Date(connectedAtMillis)
        val pattern = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return pattern.format(date)
    }

    fun adbConnectionDurationMs(): Long {
        val startedAt = adbConnectStartedAtMs ?: return adbConnectElapsedMs
        val elapsed = System.currentTimeMillis() - startedAt
        if (connectedAtMillis != 0L) {
            adbConnectElapsedMs = elapsed
        }
        return adbConnectElapsedMs.coerceAtLeast(0L)
    }

    fun setStateListener(listener: (() -> Unit)?) {
        stateListener = listener
        listener?.invoke()
    }

    private fun notifyStateChanged() {
        stateListener?.invoke()
    }

    /**
     * Establish the TCP connection on demand and immediately request the PC
     * name. This is the explicit entry point used by the ADB button.
     */
    fun connectAndRequestPcName() {
        if (adbConnectStartedAtMs == null) {
            adbConnectStartedAtMs = System.currentTimeMillis()
        }
        startIfNeeded()

        try {
            ensureConnected()
            adbConnectElapsedMs = adbConnectStartedAtMs?.let {
                System.currentTimeMillis() - it
            } ?: 0L
        } catch (_: Exception) {
            // Receiver may not be listening yet. The queued request will still be
            // processed by the worker once the socket is reachable.
        }

        enqueueCommand("GET_PC_NAME")
    }

    /**
     * Ask the Windows receiver for the current host name.
     * The reply is read by the connection worker.
     */
    fun requestPcName() {
        connectAndRequestPcName()
    }

    // MOVEはまとめる
    private var pendingDx = 0
    private var pendingDy = 0

    // CLICKなどの命令はこちら
    private val commandQueue = ArrayDeque<String>()

    fun start() {

        synchronized(lock) {
            if (running && worker?.isAlive == true) return
            running = true
            connectionState = ConnectionState.ACTIVE
        }

        worker = Thread({
            runWorker()
        }, "MyMouseMove-Connection").apply {
            start()
        }
    }

    private fun startIfNeeded() {
        synchronized(lock) {
            if (!running || worker?.isAlive != true) {
                running = true
                connectionState = ConnectionState.ACTIVE
            }
        }

        if (worker == null || worker?.isAlive != true) {
            worker = Thread({
                runWorker()
            }, "MyMouseMove-Connection").apply {
                start()
            }
        }
    }

    // -------------------------
    // マウス移動
    // -------------------------
    fun sendMove(dx: Int, dy: Int) {

        if (!running) return

        synchronized(lock) {

            pendingDx += dx
            pendingDy += dy

            lock.notifyAll()
        }
    }

    // -------------------------
    // CLICK等の通常コマンド
    // -------------------------
    fun sendCommand(command: String) {

        if (!running) return

        synchronized(lock) {
            commandQueue.addLast(command)
            lock.notifyAll()
        }
    }

    private fun enqueueCommand(command: String) {
        synchronized(lock) {
            commandQueue.addLast(command)
            lock.notifyAll()
        }
    }

    /**
     * Send the receiver shutdown command before this Android process closes.
     * EXIT is placed at the front of the queue and pending pointer movement is
     * discarded so shutdown is not delayed by old input.
     */
    fun requestRemoteExit() {

        val latch = java.util.concurrent.CountDownLatch(1)

        synchronized(lock) {
            if (!running) {
                connectionState = ConnectionState.STOPPED
                closeConnection()
                return
            }

            pendingDx = 0
            pendingDy = 0
            commandQueue.clear()
            commandQueue.addFirst("EXIT")
            connectionState = ConnectionState.EXITING
            exitRequestLatch = latch
            lock.notifyAll()
        }

        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        closeConnection()
    }

    // -------------------------
    // 送信worker
    // -------------------------
    private fun runWorker() {

        while (running) {

            var moveDx = 0
            var moveDy = 0
            var command: String? = null

            synchronized(lock) {

                while (
                    running &&
                    pendingDx == 0 &&
                    pendingDy == 0 &&
                    commandQueue.isEmpty()
                ) {

                    try {
                        lock.wait()
                    } catch (_: InterruptedException) {
                        return
                    }
                }

                if (!running) break

                /*
                 * CLICKなどの命令を優先
                 */
                if (commandQueue.isNotEmpty()) {

                    command = commandQueue.removeFirst()

                } else {

                    /*
                     * MOVEをまとめて取得
                     */
                    moveDx = pendingDx
                    moveDy = pendingDy

                    pendingDx = 0
                    pendingDy = 0
                }
            }

            try {

                ensureConnected()

                if (command != null) {

                    sendLine(command!!)

                    if (command == "GET_PC_NAME") {
                        readPcNameReply()
                    }

                    if (
                        command == "EXIT" ||
                        command == "EXIT_LOG_ON" ||
                        command == "EXIT_LOG_OFF" ||
                        command == "DISCONNECT"
                    ) {
                        running = false
                        connectionState = ConnectionState.STOPPED
                        exitRequestLatch?.countDown()
                        exitRequestLatch = null
                        closeConnection()
                        break
                    }

                } else if (moveDx != 0 || moveDy != 0) {

                    sendLine(
                        "MOVE,$moveDx,$moveDy"
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()

                closeConnection()
            }
        }

        closeConnection()
    }

    // -------------------------
    // TCP送信
    // -------------------------
    private fun sendLine(message: String) {

        writer?.write(message)
        writer?.newLine()
        writer?.flush()
    }

    // -------------------------
    // 接続確認・接続
    // -------------------------
    private fun ensureConnected() {

        val currentSocket = socket

        if (
            currentSocket != null &&
            currentSocket.isConnected &&
            !currentSocket.isClosed &&
            writer != null
        ) {
            return
        }

        closeConnection()

        val newSocket = Socket(
            "127.0.0.1",
            5001
        ).apply {

            tcpNoDelay = true
            keepAlive = true
        }

        socket = newSocket
        connectedAtMillis = System.currentTimeMillis()
        if (adbConnectStartedAtMs != null) {
            adbConnectElapsedMs = System.currentTimeMillis() - adbConnectStartedAtMs!!
        }

        reader = BufferedReader(
            InputStreamReader(
                newSocket.getInputStream(),
                Charsets.UTF_8
            )
        )

        writer = BufferedWriter(
            OutputStreamWriter(
                newSocket.getOutputStream(),
                Charsets.UTF_8
            )
        )
    }

    private fun readPcNameReply() {

        val currentSocket = socket ?: return
        val currentReader = reader ?: return

        try {
            currentSocket.soTimeout = 1500

            val reply = currentReader.readLine()

            connectedPcName =
                if (reply != null && reply.startsWith("PC_NAME,")) {
                    reply.substringAfter("PC_NAME,")
                        .ifBlank { "Unknown PC" }
                } else {
                    "Unknown PC"
                }

        } catch (_: Exception) {
            connectedPcName = "Unknown PC"

        } finally {
            notifyStateChanged()
            try {
                currentSocket.soTimeout = 0
            } catch (_: Exception) {
            }
        }
    }

    // -------------------------
    // 終了
    // -------------------------
    /**
     * Graceful disconnect requested by the user.
     *
     * This sends the receiver a DISCONNECT command and waits for the worker to
     * finish the TCP shutdown sequence naturally.
     */
    fun disconnect() {
        synchronized(lock) {
            if (!running) {
                connectionState = ConnectionState.STOPPED
                closeConnection()
                return
            }

            pendingDx = 0
            pendingDy = 0
            commandQueue.clear()
            commandQueue.addLast("DISCONNECT")
            connectionState = ConnectionState.DISCONNECTING
            lock.notifyAll()
        }
    }

    /**
     * Forced stop for app shutdown / lifecycle teardown.
     *
     * This does not send a protocol disconnect command; it immediately tears
     * down the socket and worker state.
     */
    fun stop() {
        synchronized(lock) {
            running = false
            connectionState = ConnectionState.STOPPED
            pendingDx = 0
            pendingDy = 0
            commandQueue.clear()
            lock.notifyAll()
        }

        worker?.interrupt()
        worker = null

        closeConnection()
    }

    private fun closeConnection() {

        connectionState = ConnectionState.STOPPED
        connectedPcName = "Not connected"
        connectedAtMillis = 0L
        adbConnectStartedAtMs = null
        adbConnectElapsedMs = 0L
        notifyStateChanged()

        try {
            reader?.close()
        } catch (_: Exception) {
        }

        try {
            writer?.close()
        } catch (_: Exception) {
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        reader = null
        writer = null
        socket = null
    }
}