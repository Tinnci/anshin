package com.driezy.medlog.voice

class VoiceTranscriptAppender(initialText: String) {
    private var committedText = initialText

    fun preview(interimText: String, insertSeparator: Boolean = false): String =
        join(committedText, interimText, insertSeparator)

    fun commit(finalText: String, insertSeparator: Boolean = false): String {
        committedText = join(committedText, finalText, insertSeparator)
        return committedText
    }

    private fun join(prefix: String, suffix: String, insertSeparator: Boolean): String {
        if (suffix.isBlank()) return prefix
        if (!insertSeparator || prefix.isBlank() || prefix.last().isWhitespace()) return prefix + suffix
        return "$prefix $suffix"
    }
}
