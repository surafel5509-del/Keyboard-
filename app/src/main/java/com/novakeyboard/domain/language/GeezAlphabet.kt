package com.novakeyboard.domain.language

/**
 * The Ethiopic (Geez) syllabary used by the Amharic keyboard.
 *
 * Every consonant is a contiguous run of seven code points in the Unicode
 * Ethiopic block: order 1 (ä), 2 (u), 3 (i), 4 (a), 5 (e), 6 (ə/plain) and
 * 7 (o). Labialised forms ("kwa", "qwa", ...) are separate code point sprites
 * in the Unicode block and are only defined for the consonants that have them.
 */
object GeezAlphabet {

    const val ORDER_1 = 0 // ሀ  (ä)
    const val ORDER_2 = 1 // ሁ  (u)
    const val ORDER_3 = 2 // ሂ  (i)
    const val ORDER_4 = 3 // ሃ  (a)
    const val ORDER_5 = 4 // ሄ  (e)
    const val ORDER_6 = 5 // ህ  (ə / bare consonant)
    const val ORDER_7 = 6 // ሆ  (o)

    /** A consonant series: its Latin key and its first (1st order) code point. */
    class Series(
        val key: String,
        val base: Int,
        val latinName: String,
        private val labializedBase: Int = 0,
    ) {
        /** The syllable for [order] (0..6) in this series. */
        fun letter(order: Int): String {
            val index = order.coerceIn(0, 6)
            return String(Character.toChars(base + index))
        }

        /** True when this consonant has labialised ("-wa") forms. */
        val hasLabialized: Boolean get() = labializedBase != 0

        /**
         * Labialised syllable (kwa, qwi, ...). Only the 1st, 3rd, 4th, 5th and
         * 6th orders exist in Unicode; other orders fall back to the 4th.
         */
        fun labializedLetter(order: Int): String? {
            if (!hasLabialized) return null
            return when (order) {
                ORDER_1 -> String(Character.toChars(labializedBase))
                ORDER_3 -> String(Character.toChars(labializedBase + 2))
                ORDER_4 -> String(Character.toChars(labializedBase + 3))
                ORDER_5 -> String(Character.toChars(labializedBase + 4))
                ORDER_6 -> String(Character.toChars(labializedBase + 5))
                else -> String(Character.toChars(labializedBase + 3))
            }
        }

        fun orders(): List<String> = (0..6).map { letter(it) }
    }

    val series: List<Series> = listOf(
        Series("h", 0x1200, "hä"),
        Series("l", 0x1208, "lä"),
        Series("H", 0x1210, "ḥä"),
        Series("m", 0x1218, "mä"),
        Series("s", 0x1230, "sä"),
        Series("sz", 0x1220, "śä"),
        Series("r", 0x1228, "rä"),
        Series("S", 0x1238, "šä"),
        Series("q", 0x1240, "qä", labializedBase = 0x1248),
        Series("b", 0x1260, "bä"),
        Series("v", 0x1268, "vä"),
        Series("t", 0x1270, "tä"),
        Series("c", 0x1278, "čä"),
        Series("x", 0x1280, "ḫä", labializedBase = 0x1288),
        Series("n", 0x1290, "nä"),
        Series("N", 0x1298, "ñä"),
        Series("a", 0x12A0, "ʾä"),
        Series("k", 0x12A8, "kä", labializedBase = 0x12B0),
        Series("K", 0x12B8, "ḵä"),
        Series("w", 0x12C8, "wä"),
        Series("A", 0x12D0, "ʿä"),
        Series("z", 0x12D8, "zä"),
        Series("Z", 0x12E0, "žä"),
        Series("y", 0x12E8, "yä"),
        Series("d", 0x12F0, "dä"),
        Series("j", 0x1300, "ǧä"),
        Series("g", 0x1308, "gä", labializedBase = 0x1310),
        Series("G", 0x1318, "ŋä"),
        Series("T", 0x1320, "ṭä"),
        Series("C", 0x1328, "č̣ä"),
        Series("P", 0x1330, "p̣ä"),
        Series("Y", 0x1338, "ṣä"),
        Series("F", 0x1340, "ṣ́ä"),
        Series("f", 0x1348, "fä"),
        Series("p", 0x1350, "pä"),
    )

    /** Multi-character Latin sequences recognised before single keys. */
    val digraphs: Map<String, String> = mapOf(
        "sh" to "S",
        "sz" to "sz",
        "ch" to "c",
        "ts" to "Y",
        "ny" to "N",
        "ng" to "G",
        "kh" to "K",
        "zh" to "Z",
        "th" to "t",
        "dh" to "d",
        "ph" to "f",
    )

    private val byKey: Map<String, Series> = series.associateBy { it.key }

    fun find(key: String): Series? = byKey[key]

    /** Stand-alone vowel series (አ ኡ ኢ ኣ ኤ እ ኦ). */
    val alef: Series = byKey.getValue("a")

    /**
     * Maps a Latin vowel to a Geez order, following the scheme Amharic typists
     * already know: e → 1st order (ሰ), u → 2nd (ሱ), i → 3rd (ሲ), a → 4th
     * (ሳ), o → 7th (ሶ). The 5th order has no single vowel key; "ie" (handled by
     * the transliterator) produces it, and capital `A` is kept as an alias.
     */
    fun orderForVowel(vowel: String): Int = when (vowel) {
        "a" -> ORDER_4
        "e" -> ORDER_1
        "i" -> ORDER_3
        "o" -> ORDER_7
        "u" -> ORDER_2
        "A" -> ORDER_5
        else -> ORDER_6
    }

    /** A stand-alone vowel, e.g. "a" → ኣ, "e" → አ. */
    fun vowelLetter(vowel: String): String = alef.letter(orderForVowel(vowel))

    val punctuation: Map<Char, String> = mapOf(
        ',' to "፣",
        '.' to "።",
        ';' to "፤",
        ':' to "፥",
        '?' to "፧",
        '!' to "!",
        '-' to "-",
    )

    /** Geez (Ethiopic) numerals for 1, 10, 20, ... used on the symbol page. */
    val numerals: List<String> = listOf("፩", "፪", "፫", "፬", "፭", "፮", "፯", "፰", "፱", "፲", "፳", "፴", "፵", "፶", "፷", "፸", "፹", "፺", "፻", "፼")

    /** Rows for the direct Geez keyboard (5 rows x 7 columns of consonants). */
    val layoutRows: List<List<String>> = listOf(
        listOf("ሀ", "ለ", "ሐ", "መ", "ሠ", "ረ", "ሰ"),
        listOf("ሸ", "ቀ", "በ", "ተ", "ቸ", "ኀ", "ነ"),
        listOf("ኘ", "አ", "ከ", "ኸ", "ወ", "ዐ", "ዘ"),
        listOf("ዠ", "የ", "ደ", "ጀ", "ገ", "ጘ", "ጠ"),
        listOf("ጨ", "ጰ", "ጸ", "ፀ", "ፈ", "ፐ", "ቨ"),
    )

    /** Ethiopic punctuation shown on the last row of the direct Geez keyboard. */
    val extraRow: List<String> = listOf("፣", "፤", "፥", "፧", "።")

    /** All seven orders of the series that starts with [letter], or empty. */
    fun ordersFor(letter: String): List<String> {
        if (letter.isEmpty()) return emptyList()
        val codePoint = letter.codePointAt(0)
        val match = series.firstOrNull { codePoint in it.base..(it.base + 6) }
        return match?.orders() ?: emptyList()
    }
}
