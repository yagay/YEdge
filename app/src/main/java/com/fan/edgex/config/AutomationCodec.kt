package com.fan.edgex.config

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AutomationCodec {
    fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    fun variableName(value: String): String =
        value.trim()
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .take(64)
            .ifBlank { "variable" }

    fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
