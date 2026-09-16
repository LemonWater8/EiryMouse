package com.example.diazymouse.bhid.report

/**
 * Minimal boot-protocol-like mouse input payload used with Report ID 1.
 * Payload bytes passed to BluetoothHidDevice.sendReport() do not include the report ID.
 */
object HidMousePacket {
    const val REPORT_ID: Int = 1
    const val LEFT: Byte = 0x01
    const val RIGHT: Byte = 0x02
    const val MIDDLE: Byte = 0x04

    val descriptor: ByteArray = byteArrayOf(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x01,    // Report ID 1
        0x09, 0x01,             // Usage (Pointer)
        0xA1.toByte(), 0x00,    // Collection (Physical)
        0x05, 0x09,             // Usage Page (Button)
        0x19, 0x01,             // Usage Minimum 1
        0x29, 0x03,             // Usage Maximum 3
        0x15, 0x00,             // Logical Minimum 0
        0x25, 0x01,             // Logical Maximum 1
        0x95.toByte(), 0x03,    // Report Count 3
        0x75, 0x01,             // Report Size 1
        0x81.toByte(), 0x02,    // Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    // Report Count 1
        0x75, 0x05,             // Report Size 5
        0x81.toByte(), 0x03,    // Input (Const,Var,Abs)
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x30,             // X
        0x09, 0x31,             // Y
        0x09, 0x38,             // Wheel
        0x15, 0x81.toByte(),    // -127
        0x25, 0x7F,             // 127
        0x75, 0x08,             // Report Size 8
        0x95.toByte(), 0x03,    // Report Count 3
        0x81.toByte(), 0x06,    // Input (Data,Var,Rel)
        0xC0.toByte(),
        0xC0.toByte()
    )

    fun encode(buttons: Byte = 0, dx: Int = 0, dy: Int = 0, wheel: Int = 0): ByteArray =
        byteArrayOf(
            buttons,
            axis(dx).toByte(),
            axis(dy).toByte(),
            axis(wheel).toByte()
        )

    private fun axis(value: Int): Int = value.coerceIn(-127, 127)
}
