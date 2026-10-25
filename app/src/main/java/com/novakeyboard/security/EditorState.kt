package com.novakeyboard.security

/**
 * Framework-free mirror of the `android.text.InputType` / `EditorInfo` flags the
 * IME cares about. Keeping our own constants makes the privacy rules unit
 * testable on a plain JVM.
 */
object InputFlags {
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_CLASS_TEXT = 0x00000001
    const val TYPE_CLASS_NUMBER = 0x00000002
    const val TYPE_CLASS_PHONE = 0x00000003
    const val TYPE_CLASS_DATETIME = 0x00000004

    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_TEXT_VARIATION_URI = 0x00000010
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    const val TYPE_TEXT_VARIATION_EMAIL_SUBJECT = 0x00000030
    const val TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000040
    const val TYPE_TEXT_VARIATION_LONG_MESSAGE = 0x00000050
    const val TYPE_TEXT_VARIATION_PERSON_NAME = 0x00000060
    const val TYPE_TEXT_VARIATION_POSTAL_ADDRESS = 0x00000070
    const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    const val TYPE_TEXT_VARIATION_WEB_EDIT_TEXT = 0x000000a0
    const val TYPE_TEXT_VARIATION_FILTER = 0x000000b0
    const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010

    const val TYPE_TEXT_FLAG_CAP_CHARACTERS = 0x00001000
    const val TYPE_TEXT_FLAG_CAP_WORDS = 0x00002000
    const val TYPE_TEXT_FLAG_CAP_SENTENCES = 0x00004000
    const val TYPE_TEXT_FLAG_AUTO_CORRECT = 0x00008000
    const val TYPE_TEXT_FLAG_AUTO_COMPLETE = 0x00010000
    const val TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000
    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x00080000

    const val IME_MASK_ACTION = 0x000000ff
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
    const val IME_FLAG_NO_ENTER_ACTION = 0x40000000
    const val IME_FLAG_NO_FULLSCREEN = 0x02000000
    const val IME_FLAG_NO_EXTRACT_UI = 0x10000000
}

enum class KeyboardKind { TEXT, EMAIL, URI, PHONE, NUMBER, DATETIME }

enum class EnterAction { NONE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS, NEWLINE }

enum class CapMode { NONE, CHARACTERS, WORDS, SENTENCES }

/**
 * Everything the keyboard needs to know about the editor it is attached to.
 * `isSensitive` gates learning, prediction, autocorrect and clipboard capture.
 */
data class EditorState(
    val kind: KeyboardKind = KeyboardKind.TEXT,
    val enterAction: EnterAction = EnterAction.NEWLINE,
    val isPassword: Boolean = false,
    val isSensitive: Boolean = false,
    val isMultiline: Boolean = false,
    val allowsSuggestions: Boolean = true,
    val allowsPersonalization: Boolean = true,
    val capMode: CapMode = CapMode.SENTENCES,
    val inputType: Int = 1,
) {
    val enterKeyLabel: String
        get() = when (enterAction) {
            EnterAction.GO -> "Go"
            EnterAction.SEARCH -> "Search"
            EnterAction.SEND -> "Send"
            EnterAction.NEXT -> "Next"
            EnterAction.DONE -> "Done"
            EnterAction.PREVIOUS -> "Prev"
            EnterAction.NONE, EnterAction.NEWLINE -> "Enter"
        }

    companion object {
        fun from(inputType: Int, imeOptions: Int, privateOptions: String? = null): EditorState {
            val typeClass = inputType and InputFlags.TYPE_MASK_CLASS
            val variation = inputType and InputFlags.TYPE_MASK_VARIATION

            val numberPassword = typeClass == InputFlags.TYPE_CLASS_NUMBER &&
                variation == InputFlags.TYPE_NUMBER_VARIATION_PASSWORD
            val textPassword = typeClass == InputFlags.TYPE_CLASS_TEXT && (
                variation == InputFlags.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputFlags.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputFlags.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            val password = numberPassword || textPassword
            val noPersonalizedLearning = imeOptions and InputFlags.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            val privateOptOut = privateOptions?.contains("noPersonalizedLearning", ignoreCase = true) == true ||
                privateOptions?.contains("disablePersonalizedLearning", ignoreCase = true) == true
            val sensitive = password || noPersonalizedLearning || privateOptOut

            val kind = when (typeClass) {
                InputFlags.TYPE_CLASS_NUMBER, InputFlags.TYPE_CLASS_DATETIME ->
                    if (typeClass == InputFlags.TYPE_CLASS_DATETIME) KeyboardKind.DATETIME else KeyboardKind.NUMBER
                InputFlags.TYPE_CLASS_PHONE -> KeyboardKind.PHONE
                else -> when (variation) {
                    InputFlags.TYPE_TEXT_VARIATION_URI -> KeyboardKind.URI
                    InputFlags.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputFlags.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    -> KeyboardKind.EMAIL
                    else -> KeyboardKind.TEXT
                }
            }

            val multiline = inputType and InputFlags.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = imeOptions and InputFlags.IME_FLAG_NO_ENTER_ACTION != 0
            val actionOrdinal = imeOptions and InputFlags.IME_MASK_ACTION
            val enterAction = when {
                password -> EnterAction.DONE
                noEnterAction || (actionOrdinal == 0 && multiline) -> EnterAction.NEWLINE
                else -> when (actionOrdinal) {
                    2 -> EnterAction.GO
                    3 -> EnterAction.SEARCH
                    4 -> EnterAction.SEND
                    5 -> EnterAction.NEXT
                    6 -> EnterAction.DONE
                    7 -> EnterAction.PREVIOUS
                    1 -> EnterAction.NONE
                    else -> EnterAction.NEWLINE
                }
            }

            val capMode = when {
                inputType and InputFlags.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> CapMode.CHARACTERS
                inputType and InputFlags.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> CapMode.WORDS
                inputType and InputFlags.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> CapMode.SENTENCES
                else -> CapMode.NONE
            }

            val noSuggestions = inputType and InputFlags.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
            val allowsSuggestions = !sensitive && !noSuggestions

            return EditorState(
                kind = kind,
                enterAction = if (sensitive && enterAction == EnterAction.NEWLINE) EnterAction.DONE else enterAction,
                isPassword = password,
                isSensitive = sensitive,
                isMultiline = multiline,
                allowsSuggestions = allowsSuggestions,
                allowsPersonalization = !sensitive,
                capMode = capMode,
                inputType = inputType,
            )
        }
    }
}
