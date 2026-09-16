package com.example.diazymouse.bhid.report

/**
 * Standard 8-byte keyboard input report carried as Report ID 2.
 * Report ID itself is supplied separately to BluetoothHidDevice.sendReport().
 *
 * V4.2: direct keyboard bridge now supports printable half-width ASCII symbols.
 * Symbol mapping is tuned for a Japanese Windows/JIS keyboard layout because
 * this verification UI also exposes the physical 半角/全角 key.
 */
object HidKeyboardPacket {
    const val REPORT_ID: Int = 2

    // Modifier bits (byte 0)
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04
    const val MOD_LEFT_GUI: Byte = 0x08

    // USB HID Keyboard/Keypad Usage IDs used by this verification UI.
    const val KEY_A: Byte = 0x04
    const val KEY_C: Byte = 0x06
    const val KEY_X: Byte = 0x1B
    const val KEY_Z: Byte = 0x1D
    const val KEY_D: Byte = 0x07
    const val KEY_Y: Byte = 0x1C
    const val KEY_ESCAPE: Byte = 0x29
    const val KEY_F1: Byte = 0x3A
    const val KEY_F5: Byte = 0x3E
    const val KEY_F6: Byte = 0x3F
    const val KEY_F7: Byte = 0x40
    const val KEY_F8: Byte = 0x41
    const val KEY_F12: Byte = 0x45
    const val KEY_ENTER: Byte = 0x28
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_SPACE: Byte = 0x2C
    const val KEY_TAB: Byte = 0x2B
    const val KEY_PRINT_SCREEN: Byte = 0x46
    const val KEY_HOME: Byte = 0x4A
    const val KEY_END: Byte = 0x4D
    // USB HID International4: Japanese Henkan / Convert key.
    const val KEY_HENKAN: Byte = 0x8A.toByte()
    // USB HID International5: Japanese Muhenkan / NonConvert key.
    const val KEY_MUHENKAN: Byte = 0x8B.toByte()
    const val KEY_RIGHT_ARROW: Byte = 0x4F
    const val KEY_LEFT_ARROW: Byte = 0x50
    const val KEY_DOWN_ARROW: Byte = 0x51
    const val KEY_UP_ARROW: Byte = 0x52
    const val KEY_DELETE: Byte = 0x4C
    // On a Japanese Windows keyboard layout, HID Usage 0x35 is the physical 半角/全角 key.
    const val KEY_HANKAKU_ZENKAKU: Byte = 0x35

    data class Stroke(val key: Byte, val modifiers: Byte = 0)

    private val alphabetMap: Map<Char, Stroke> = buildMap {
        ('a'..'z').forEachIndexed { index, ch ->
            put(ch, Stroke((0x04 + index).toByte()))
            put(ch.uppercaseChar(), Stroke((0x04 + index).toByte(), MOD_LEFT_SHIFT))
        }
    }

    private val digitMap: Map<Char, Stroke> = mapOf(
        '1' to Stroke(0x1E), '2' to Stroke(0x1F), '3' to Stroke(0x20), '4' to Stroke(0x21), '5' to Stroke(0x22),
        '6' to Stroke(0x23), '7' to Stroke(0x24), '8' to Stroke(0x25), '9' to Stroke(0x26), '0' to Stroke(0x27)
    )

    private val symbolMap: Map<Char, Stroke> = mapOf(
        ' ' to Stroke(KEY_SPACE),
        '!' to Stroke(0x1E, MOD_LEFT_SHIFT),
        '"' to Stroke(0x1F, MOD_LEFT_SHIFT),
        '#' to Stroke(0x20, MOD_LEFT_SHIFT),
        '$' to Stroke(0x21, MOD_LEFT_SHIFT),
        '%' to Stroke(0x22, MOD_LEFT_SHIFT),
        '&' to Stroke(0x23, MOD_LEFT_SHIFT),
        '\'' to Stroke(0x24, MOD_LEFT_SHIFT),
        '(' to Stroke(0x25, MOD_LEFT_SHIFT),
        ')' to Stroke(0x26, MOD_LEFT_SHIFT),
        '-' to Stroke(0x2D),
        '=' to Stroke(0x2D, MOD_LEFT_SHIFT),
        '^' to Stroke(0x2E),
        '~' to Stroke(0x2E, MOD_LEFT_SHIFT),
        '@' to Stroke(0x2F),
        '`' to Stroke(0x2F, MOD_LEFT_SHIFT),
        '[' to Stroke(0x30),
        '{' to Stroke(0x30, MOD_LEFT_SHIFT),
        ']' to Stroke(0x31),
        '}' to Stroke(0x31, MOD_LEFT_SHIFT),
        ';' to Stroke(0x33),
        '+' to Stroke(0x33, MOD_LEFT_SHIFT),
        ':' to Stroke(0x34),
        '*' to Stroke(0x34, MOD_LEFT_SHIFT),
        ',' to Stroke(0x36),
        '<' to Stroke(0x36, MOD_LEFT_SHIFT),
        '.' to Stroke(0x37),
        '>' to Stroke(0x37, MOD_LEFT_SHIFT),
        '/' to Stroke(0x38),
        '?' to Stroke(0x38, MOD_LEFT_SHIFT),
        '\\' to Stroke(0x87.toByte()),
        '_' to Stroke(0x87.toByte(), MOD_LEFT_SHIFT),
        '|' to Stroke(0x89.toByte(), MOD_LEFT_SHIFT)
    )

    private val qwertyMap: Map<Char, Stroke> = alphabetMap + digitMap + symbolMap

    fun encode(modifiers: Byte = 0, vararg keys: Byte): ByteArray {
        val report = ByteArray(8)
        report[0] = modifiers
        report[1] = 0 // reserved
        keys.take(6).forEachIndexed { index, key -> report[index + 2] = key }
        return report
    }

    fun neutral(): ByteArray = encode()

    fun strokeFor(char: Char): Stroke? = qwertyMap[char]

    fun keyCodeFor(char: Char): Byte? = strokeFor(char)?.key

    fun modifierFor(char: Char): Byte = strokeFor(char)?.modifiers ?: 0

    fun isSupportedDirectInput(text: String): Boolean = text.all { strokeFor(it) != null }

    // Kept for compatibility with existing test/call sites.
    fun isAllowedAsciiInput(text: String): Boolean = isSupportedDirectInput(text)

    fun textToKeyEvents(text: String): List<Pair<Byte, Byte>> {
        if (!isSupportedDirectInput(text)) return emptyList()
        return text.mapNotNull { ch ->
            val stroke = strokeFor(ch) ?: return@mapNotNull null
            stroke.key to stroke.modifiers
        }
    }
}
