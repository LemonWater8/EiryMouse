package com.example.diazymouse.bhid.report

/**
 * Composite HID report descriptor used by BluetoothHidCheck v16.0.
 *
 * Report ID 1: mouse
 * Report ID 2: keyboard
 * Report ID 3: consumer control
 *
 * The mouse collection intentionally preserves the report shape used by the
 * previously working verification build. Keyboard and Consumer collections are
 * appended so each function can be tested independently over one HID link.
 */
object CompositeHidDescriptor {
    val descriptor: ByteArray = byteArrayOf(
        // -----------------------------------------------------------------
        // Report ID 1 - Mouse (3 buttons + X/Y/Wheel)
        // -----------------------------------------------------------------
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x01,    //   Report ID (1)
        0x09, 0x01,             //   Usage (Pointer)
        0xA1.toByte(), 0x00,    //   Collection (Physical)
        0x05, 0x09,             //     Usage Page (Button)
        0x19, 0x01,             //     Usage Minimum (1)
        0x29, 0x03,             //     Usage Maximum (3)
        0x15, 0x00,             //     Logical Minimum (0)
        0x25, 0x01,             //     Logical Maximum (1)
        0x95.toByte(), 0x03,    //     Report Count (3)
        0x75, 0x01,             //     Report Size (1)
        0x81.toByte(), 0x02,    //     Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //     Report Count (1)
        0x75, 0x05,             //     Report Size (5)
        0x81.toByte(), 0x03,    //     Input (Const,Var,Abs)
        0x05, 0x01,             //     Usage Page (Generic Desktop)
        0x09, 0x30,             //     Usage (X)
        0x09, 0x31,             //     Usage (Y)
        0x09, 0x38,             //     Usage (Wheel)
        0x15, 0x81.toByte(),    //     Logical Minimum (-127)
        0x25, 0x7F,             //     Logical Maximum (127)
        0x75, 0x08,             //     Report Size (8)
        0x95.toByte(), 0x03,    //     Report Count (3)
        0x81.toByte(), 0x06,    //     Input (Data,Var,Rel)
        0xC0.toByte(),          //   End Collection
        0xC0.toByte(),          // End Collection

        // -----------------------------------------------------------------
        // Report ID 2 - Keyboard (modifier + reserved + six keys)
        // -----------------------------------------------------------------
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x02,    //   Report ID (2)
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0.toByte(),    //   Usage Minimum (Left Control)
        0x29, 0xE7.toByte(),    //   Usage Maximum (Right GUI)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x08,    //   Report Count (8)
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs) modifiers
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x03,    //   Input (Const,Var,Abs) reserved
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x73,             //   Usage Maximum (Keyboard F24)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x73,             //   Logical Maximum (115)
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x06,    //   Report Count (6)
        0x81.toByte(), 0x00,    //   Input (Data,Array,Abs)
        0xC0.toByte(),          // End Collection

        // -----------------------------------------------------------------
        // Report ID 3 - Consumer Control
        // bits: Mute, Vol+, Vol-, Play/Pause, Next, Previous
        // -----------------------------------------------------------------
        0x05, 0x0C,             // Usage Page (Consumer)
        0x09, 0x01,             // Usage (Consumer Control)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x03,    //   Report ID (3)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x09, 0xE2.toByte(),    //   Usage (Mute)
        0x09, 0xE9.toByte(),    //   Usage (Volume Increment)
        0x09, 0xEA.toByte(),    //   Usage (Volume Decrement)
        0x09, 0xCD.toByte(),    //   Usage (Play/Pause)
        0x09, 0xB5.toByte(),    //   Usage (Scan Next Track)
        0x09, 0xB6.toByte(),    //   Usage (Scan Previous Track)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x06,    //   Report Count (6)
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x75, 0x02,             //   Report Size (2)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x03,    //   Input (Const,Var,Abs) padding
        0xC0.toByte()           // End Collection
    )
}
