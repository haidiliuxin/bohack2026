package com.neurogarden.app.agent

object ChatTextSanitizer {
    private val mojibakeTokens = listOf(
        "鎴", "浣", "涓", "鍙", "鐨", "杩", "瀹", "绱", "闄", "鍚",
        "閺", "閸", "鈥", "€", "锛", "銆", "鍩", "鐤", "缁", "妫"
    )

    fun cleanAssistantReply(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank() || looksCorrupted(trimmed)) {
            return "我在这里。你可以慢慢说，不需要一次讲完整；也可以只发一个词，我会跟着你的节奏来。"
        }
        return trimmed
    }

    fun cleanShortText(text: String, fallback: String): String {
        val trimmed = text.trim()
        return if (trimmed.isBlank() || looksCorrupted(trimmed)) fallback else trimmed
    }

    fun looksCorrupted(text: String): Boolean {
        if (text.contains('\uFFFD')) return true
        val hits = mojibakeTokens.count { text.contains(it) }
        return hits >= 3
    }
}
