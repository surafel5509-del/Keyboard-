package com.novakeyboard.ui.keyboard

import com.novakeyboard.domain.language.GeezAlphabet
import com.novakeyboard.domain.language.Language
import com.novakeyboard.security.EnterAction

/** Builds the immutable key layouts shown by [KeyboardView]. */
object KeyboardLayouts {

    private val LETTER_ALTERNATES: Map<String, List<String>> = mapOf(
        "a" to listOf("á", "à", "â", "ä", "ã", "å", "æ"),
        "c" to listOf("ç", "ć", "č"),
        "e" to listOf("é", "è", "ê", "ë", "ē"),
        "i" to listOf("í", "ì", "î", "ï", "ī"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø", "ō"),
        "s" to listOf("ß", "š", "ś"),
        "u" to listOf("ú", "ù", "û", "ü", "ū"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł"),
        "r" to listOf("ř"),
        "t" to listOf("ť"),
        "g" to listOf("ğ"),
        "d" to listOf("ď"),
    )

    private val QWERTY_ROWS = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )

    private val NUMBER_TOPS = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")

    fun build(
        language: Language,
        kind: LayoutKind,
        uppercase: Boolean,
        enterAction: EnterAction,
        numberRow: Boolean,
    ): KeyLayout = when (kind) {
        LayoutKind.LETTERS -> letters(language, uppercase, enterAction, numberRow)
        LayoutKind.GEEZ_LETTERS -> geezLetters(enterAction)
        LayoutKind.SYMBOLS -> symbols(language, enterAction, second = false)
        LayoutKind.SYMBOLS_2 -> symbols(language, enterAction, second = true)
        LayoutKind.NUMBER -> numberPad(language, enterAction)
        LayoutKind.PHONE -> phonePad(language, enterAction)
        LayoutKind.EMAIL -> letters(language, uppercase, enterAction, numberRow = false, email = true)
        LayoutKind.URI -> letters(language, uppercase, enterAction, numberRow = false, uri = true)
    }

    private fun enterIcon(action: EnterAction): KeyIcon = when (action) {
        EnterAction.SEARCH -> KeyIcon.SEARCH
        EnterAction.SEND -> KeyIcon.SEND
        EnterAction.NEXT -> KeyIcon.NEXT
        EnterAction.DONE -> KeyIcon.DONE
        EnterAction.GO -> KeyIcon.GO
        else -> KeyIcon.ENTER
    }

    private fun letters(
        language: Language,
        uppercase: Boolean,
        enterAction: EnterAction,
        numberRow: Boolean,
        email: Boolean = false,
        uri: Boolean = false,
    ): KeyLayout {
        val rows = ArrayList<KeyRow>()
        if (numberRow) rows.add(numberRowKeys())
        rows.add(KeyRow(QWERTY_ROWS[0].map { charKey(it, uppercase) }))
        val second = ArrayList<Key>()
        second.add(Key.spacer(0.5f))
        QWERTY_ROWS[1].forEach { second.add(charKey(it, uppercase)) }
        second.add(Key.spacer(0.5f))
        rows.add(KeyRow(second))

        val third = ArrayList<Key>()
        third.add(Key.shift())
        QWERTY_ROWS[2].forEach { third.add(charKey(it, uppercase)) }
        third.add(Key.backspace())
        rows.add(KeyRow(third))

        val bottom = ArrayList<Key>()
        bottom.add(Key.toSymbols())
        if (email) {
            bottom.add(charKey("@", uppercase, weight = 1.1f))
            bottom.add(charKey(".", uppercase))
        } else if (uri) {
            bottom.add(charKey("/", uppercase))
            bottom.add(charKey(".", uppercase))
        } else {
            bottom.add(Key.language())
            bottom.add(charKey(",", uppercase))
        }
        bottom.add(Key.space())
        if (!email && !uri) bottom.add(charKey(".", uppercase))
        bottom.add(Key.enter(enterIcon(enterAction)))
        rows.add(KeyRow(bottom))

        return KeyLayout(LayoutKind.LETTERS, language, rows)
    }

    private fun charKey(letter: String, uppercase: Boolean = false, weight: Float = 1f): Key {
        val alternates = LETTER_ALTERNATES[letter].orEmpty()
        return Key(
            type = KeyType.CHAR,
            label = if (uppercase) letter.uppercase() else letter,
            output = if (uppercase) letter.uppercase() else letter,
            shiftedOutput = letter.uppercase(),
            alternates = alternates,
            weight = weight,
        )
    }

    private fun numberRowKeys(): KeyRow = KeyRow(
        (1..9).map { digit ->
            val char = digit.toString()
            Key(
                type = KeyType.CHAR,
                label = char,
                output = char,
                shiftedOutput = char,
                alternates = listOfNotNull(NUMBER_TOPS[digit - 1]),
                topLabel = NUMBER_TOPS[digit - 1],
                weight = 1f,
            )
        } + Key(
            type = KeyType.CHAR,
            label = "0",
            output = "0",
            shiftedOutput = "0",
            alternates = listOf(")"),
            topLabel = ")",
            weight = 1f,
        ),
    )

    private fun symbols(language: Language, enterAction: EnterAction, second: Boolean): KeyLayout {
        val rows = ArrayList<KeyRow>()

        if (!second) {
            rows.add(KeyRow((1..9).map { charKey(it.toString()) } + charKey("0")))
            rows.add(
                KeyRow(
                    listOf(
                        charKey("@"), charKey("#"), charKey("$"), charKey("%"), charKey("&"),
                        charKey("*"), charKey("-"), charKey("+"), charKey("("), charKey(")"),
                    ),
                ),
            )
            val third = ArrayList<Key>()
            third.add(Key.toLetters())
            listOf("/", "\\", "\"", "'", ":", ";", "!", "?").forEach { third.add(charKey(it)) }
            third.add(Key.backspace())
            rows.add(KeyRow(third))
        } else {
            rows.add(
                KeyRow(
                    listOf(
                        charKey("~"), charKey("`"), charKey("^"), charKey("°"), charKey("µ"),
                        charKey("§"), charKey("±"), charKey("×"), charKey("÷"), charKey("="),
                    ),
                ),
            )
            rows.add(
                KeyRow(
                    listOf(
                        charKey("{"), charKey("}"), charKey("["), charKey("]"), charKey("<"),
                        charKey(">"), charKey("€"), charKey("£"), charKey("¥"), charKey("¢"),
                    ),
                ),
            )
            val third = ArrayList<Key>()
            third.add(Key.toLetters())
            listOf("_", "|", "•", "√", "π", "∆", "∞", "₿").forEach { third.add(charKey(it)) }
            third.add(Key.backspace())
            rows.add(KeyRow(third))
        }

        val bottom = ArrayList<Key>()
        bottom.add(if (second) Key.toSymbols() else Key.toLetters())
        if (language == Language.AMHARIC) {
            bottom.add(charKey("።"))
            bottom.add(charKey("፣"))
        } else {
            bottom.add(charKey(","))
            bottom.add(charKey("."))
        }
        bottom.add(Key.space())
        bottom.add(if (second) Key.toSymbols() else Key.toSymbols2())
        bottom.add(Key.enter(enterIcon(enterAction)))
        rows.add(KeyRow(bottom))
        return KeyLayout(if (second) LayoutKind.SYMBOLS_2 else LayoutKind.SYMBOLS, language, rows)
    }

    private fun numberPad(language: Language, enterAction: EnterAction): KeyLayout {
        val rows = listOf(
            KeyRow((1..9).map { charKey(it.toString()) } + charKey("0")),
            KeyRow(
                listOf(
                    charKey("@"), charKey("#"), charKey("$"), charKey("%"), charKey("&"),
                    charKey("*"), charKey("-"), charKey("+"), charKey("("), charKey(")"),
                ),
            ),
            KeyRow(
                listOf(
                    Key.toLetters(), charKey("/"), charKey("\\"), charKey("\""), charKey("'"),
                    charKey(":"), charKey(";"), charKey("!"), charKey("?"), Key.backspace(),
                ),
            ),
            KeyRow(listOf(Key.toSymbols2(), charKey(","), charKey("."), Key.space(), Key.enter(enterIcon(enterAction)))),
        )
        return KeyLayout(LayoutKind.NUMBER, language, rows)
    }

    private fun phonePad(language: Language, enterAction: EnterAction): KeyLayout {
        val rows = listOf(
            KeyRow(listOf("1", "2", "3").map { charKey(it) } + Key.backspace()),
            KeyRow(listOf("4", "5", "6").map { charKey(it) } + charKey("-")),
            KeyRow(listOf("7", "8", "9").map { charKey(it) } + charKey("+")),
            KeyRow(listOf(charKey("*"), charKey("0"), charKey("#"), Key.enter(enterIcon(enterAction)))),
        )
        return KeyLayout(LayoutKind.PHONE, language, rows)
    }

    private fun geezLetters(enterAction: EnterAction): KeyLayout {
        val rows = ArrayList<KeyRow>()
        for (row in GeezAlphabet.layoutRows) {
            rows.add(KeyRow(row.map { geezKey(it) }))
        }
        val fourth = ArrayList<Key>()
        fourth.add(Key.toSymbols())
        for (key in GeezAlphabet.extraRow) fourth.add(geezKey(key))
        fourth.add(Key.space().copy(weight = 2.4f))
        fourth.add(Key.backspace())
        fourth.add(Key.enter(enterIcon(enterAction)))
        rows.add(KeyRow(fourth))
        return KeyLayout(LayoutKind.GEEZ_LETTERS, Language.AMHARIC, rows)
    }

    private fun geezKey(key: String): Key {
        val alternates = GeezAlphabet.ordersFor(key)
        return Key(
            type = KeyType.CHAR,
            label = key,
            output = key,
            shiftedOutput = key,
            alternates = alternates,
            weight = 1f,
        )
    }
}
