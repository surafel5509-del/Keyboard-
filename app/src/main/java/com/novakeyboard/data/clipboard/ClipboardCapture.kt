package com.novakeyboard.data.clipboard

/**
 * Placed in its own file so the capture policy can be unit-tested without
 * touching Android's clipboard APIs.
 */
object ClipboardCapturePolicy {

    /** Clipboard contents longer than this are ignored to protect memory/privacy. */
    const val MAX_CAPTURE_LENGTH = 4000

    fun shouldCapture(
        enabled: Boolean,
        enabledByUser: Boolean,
        isSensitiveField: Boolean,
        text: CharSequence?,
    ): Boolean {
        if (!enabled || !enabledByUser) return false
        if (isSensitiveField) return false
        if (text.isNullOrBlank()) return false
        if (text.length > MAX_CAPTURE_LENGTH) return false
        return true
    }
}
