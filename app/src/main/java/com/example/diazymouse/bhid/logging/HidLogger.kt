package com.example.diazymouse.bhid.logging

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HidLogger(
    context: Context,
    private val onLine: ((String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "HidCheck"
    }

    private val timestampFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val logDirectory =
        File(context.filesDir, "hid_logs").apply {
            mkdirs()
        }

    val logFile: File =
        File(
            logDirectory,
            "hidcheck_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date())
            }.log"
        )

    private var writer: BufferedWriter? =
        BufferedWriter(FileWriter(logFile, true))

    @Synchronized
    fun log(message: String) {
        val line =
            "${timestampFormat.format(Date())} " +
                "[${Thread.currentThread().name}] $message"

        Log.d(TAG, line)

        try {
            writer?.apply {
                write(line)
                newLine()
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist HID log", e)
        }

        onLine?.invoke(line)
    }

    @Synchronized
    fun close() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
        } finally {
            writer = null
        }
    }
}
