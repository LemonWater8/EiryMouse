package com.example.diazymouse.bhid.report

/**
 * One-byte Consumer Control input report carried as Report ID 3.
 * Each bit maps to one standard Consumer usage declared in CompositeHidDescriptor.
 */
object HidConsumerPacket {
    const val REPORT_ID: Int = 3

    const val MUTE: Byte = 0x01
    const val VOLUME_UP: Byte = 0x02
    const val VOLUME_DOWN: Byte = 0x04
    const val PLAY_PAUSE: Byte = 0x08
    const val NEXT_TRACK: Byte = 0x10
    const val PREVIOUS_TRACK: Byte = 0x20

    fun encode(buttons: Byte = 0): ByteArray = byteArrayOf(buttons)
    fun neutral(): ByteArray = encode()
}
