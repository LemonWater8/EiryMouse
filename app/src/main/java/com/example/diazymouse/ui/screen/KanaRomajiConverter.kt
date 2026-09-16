package com.example.diazymouse.ui.screen

/**
 * Small, deterministic kana -> romaji bridge for the Bluetooth HID experiment.
 *
 * Purpose:
 * - Gboard/Godan may commit kana to Android instead of raw latin keystrokes.
 * - Standard keyboard HID cannot send that Unicode kana directly.
 * - Convert kana-only commits back to romaji, then let the Windows IME rebuild
 *   the Japanese text on the PC side.
 *
 * This intentionally returns null for kanji/emoji/unknown characters instead
 * of guessing a reading and sending the wrong text.
 */
object KanaRomajiConverter {
    private val pairMap = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "じゃ" to "ja",  "じゅ" to "ju",  "じょ" to "jo",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "ぢゃ" to "ja",  "ぢゅ" to "ju",  "ぢょ" to "jo",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ふぁ" to "fa",  "ふぃ" to "fi",  "ふぇ" to "fe", "ふぉ" to "fo",
        "てぃ" to "thi", "でぃ" to "dhi", "とぅ" to "twu", "どぅ" to "dwu",
        "うぃ" to "wi",  "うぇ" to "we",  "うぉ" to "wo",
        "しぇ" to "she", "じぇ" to "je",  "ちぇ" to "che",
        "つぁ" to "tsa", "つぃ" to "tsi", "つぇ" to "tse", "つぉ" to "tso"
    )

    private val singleMap = mapOf(
        'あ' to "a",  'い' to "i",  'う' to "u",  'え' to "e",  'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi",'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi",'つ' to "tsu",'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        // Preserve Godan's explicit small-kana operation.  Windows IME has no
        // HID key for a kana glyph, so emit a deterministic romaji sequence
        // that reconstructs the same small kana on the PC side.
        'ぁ' to "la", 'ぃ' to "li", 'ぅ' to "lu", 'ぇ' to "le", 'ぉ' to "lo",
        'ゃ' to "lya", 'ゅ' to "lyu", 'ょ' to "lyo", 'ゎ' to "lwa",
        'ゔ' to "vu",
        '、' to ",", '。' to ".", 'ー' to "-", '！' to "!", '？' to "?",
        ' ' to " "
    )

    fun convert(text: String): String? {
        if (text.isEmpty()) return ""

        val normalized = buildString(text.length) {
            for (ch in text) {
                append(
                    when {
                        ch in 'ァ'..'ヶ' -> (ch.code - 0x60).toChar()
                        ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                        ch == '\u3000' -> ' '
                        else -> ch
                    }
                )
            }
        }

        val out = StringBuilder()
        var i = 0
        var geminate = false

        while (i < normalized.length) {
            val ch = normalized[i]

            if (ch == 'っ') {
                // Godan can explicitly change つ -> っ before another kana is
                // entered.  A trailing small-tsu must therefore be representable
                // on its own instead of being held as an unsupported state.
                if (i == normalized.lastIndex) {
                    out.append("ltu")
                    i++
                    continue
                }
                geminate = true
                i++
                continue
            }

            if (ch == 'ん') {
                // Send an already-confirmed Japanese nasal as "nn".  A single
                // HID 'n' remains a pending Latin n in Windows IME on some
                // layouts, while "nn" deterministically completes ん.
                // Godan's temporary full-width ｎ is removed before this
                // converter is called, so it is never double-counted here.
                out.append("nn")
                i++
                continue
            }

            // Gboard/Godan composing text is often mixed, e.g. "ごｄ".
            // Preserve printable ASCII after full-width normalization so the
            // composing stream can be mirrored to HID before commitText().
            if (ch.code in 0x20..0x7E) {
                out.append(ch)
                i++
                continue
            }

            val pair = if (i + 1 < normalized.length) normalized.substring(i, i + 2) else null
            val roman = pair?.let { pairMap[it] } ?: singleMap[ch] ?: return null
            val consumed = if (pair != null && pairMap.containsKey(pair)) 2 else 1

            if (geminate) {
                val first = roman.firstOrNull()
                if (first == null || first !in 'a'..'z' || first in "aiueon") return null
                out.append(first)
                geminate = false
            }

            out.append(roman)
            i += consumed
        }

        return out.toString()
    }

    private fun romanForAt(text: String, index: Int): String? {
        if (index >= text.length) return null
        if (index + 1 < text.length) {
            pairMap[text.substring(index, index + 2)]?.let { return it }
        }
        return singleMap[text[index]]
    }
}
