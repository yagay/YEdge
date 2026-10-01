package com.fan.edgex.config

import org.json.JSONArray
import org.json.JSONObject

/** Shared configuration schema for event triggers, schedules and extra hardware keys. */
object AutomationConfig {
    const val ENABLED = "automation_enabled"
    const val SCHEDULE_RULES = "automation_schedule_rules"
    const val FOREGROUND_RULES = "automation_foreground_rules"
    const val CUSTOM_KEY_RULES = "automation_custom_key_rules"

    const val APP_STATE_ENTER = "enter"
    const val APP_STATE_EXIT = "exit"

    const val EVENT_BOOT_COMPLETED = "boot_completed"
    const val EVENT_SCREEN_ON = "screen_on"
    const val EVENT_SCREEN_OFF = "screen_off"
    const val EVENT_USER_PRESENT = "user_present"
    const val EVENT_USER_UNLOCKED = "user_unlocked"
    const val EVENT_POWER_CONNECTED = "power_connected"
    const val EVENT_POWER_DISCONNECTED = "power_disconnected"
    const val EVENT_WIFI_CONNECTED = "wifi_connected"
    const val EVENT_WIFI_DISCONNECTED = "wifi_disconnected"
    const val EVENT_NETWORK_CONNECTED = "network_connected"
    const val EVENT_NETWORK_DISCONNECTED = "network_disconnected"
    const val EVENT_FULLSCREEN_ENTER = "fullscreen_enter"
    const val EVENT_FULLSCREEN_EXIT = "fullscreen_exit"
    const val EVENT_STATUS_BAR_SHOWN = "status_bar_shown"
    const val EVENT_STATUS_BAR_HIDDEN = "status_bar_hidden"
    const val EVENT_NAV_BAR_SHOWN = "nav_bar_shown"
    const val EVENT_NAV_BAR_HIDDEN = "nav_bar_hidden"

    val EVENTS = listOf(
        EVENT_BOOT_COMPLETED,
        EVENT_SCREEN_ON,
        EVENT_SCREEN_OFF,
        EVENT_USER_PRESENT,
        EVENT_USER_UNLOCKED,
        EVENT_POWER_CONNECTED,
        EVENT_POWER_DISCONNECTED,
        EVENT_WIFI_CONNECTED,
        EVENT_WIFI_DISCONNECTED,
        EVENT_NETWORK_CONNECTED,
        EVENT_NETWORK_DISCONNECTED,
        EVENT_FULLSCREEN_ENTER,
        EVENT_FULLSCREEN_EXIT,
        EVENT_STATUS_BAR_SHOWN,
        EVENT_STATUS_BAR_HIDDEN,
        EVENT_NAV_BAR_SHOWN,
        EVENT_NAV_BAR_HIDDEN,
    )

    fun eventAction(event: String) = "automation_event_${event}_action"
    fun eventActionLabel(event: String) = "${eventAction(event)}_label"

    data class ScheduleRule(
        val id: String,
        val hour: Int,
        val minute: Int,
        /** Sunday bit 0 through Saturday bit 6. 0 means every day. */
        val daysMask: Int,
        val action: String,
        val label: String = "",
        val enabled: Boolean = true,
    )

    data class ForegroundRule(
        val id: String,
        val packageName: String,
        val state: String = APP_STATE_ENTER,
        val action: String,
        val label: String = "",
        val enabled: Boolean = true,
    )

    data class CustomKeyRule(
        val id: String,
        val keyCode: Int,
        val trigger: String,
        val action: String,
        val label: String = "",
        val enabled: Boolean = true,
    )

    fun encodeSchedules(rules: Collection<ScheduleRule>): String = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("hour", rule.hour.coerceIn(0, 23))
                put("minute", rule.minute.coerceIn(0, 59))
                put("days", rule.daysMask)
                put("action", rule.action)
                put("label", rule.label)
                put("enabled", rule.enabled)
            })
        }
    }.toString()

    fun decodeSchedules(raw: String): List<ScheduleRule> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val action = obj.optString("action").trim()
                if (action.isBlank() || action == "none") continue
                add(
                    ScheduleRule(
                        id = obj.optString("id").ifBlank { "schedule_$i" },
                        hour = obj.optInt("hour", 0).coerceIn(0, 23),
                        minute = obj.optInt("minute", 0).coerceIn(0, 59),
                        daysMask = obj.optInt("days", 0),
                        action = action,
                        label = obj.optString("label"),
                        enabled = obj.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encodeForegroundRules(rules: Collection<ForegroundRule>): String = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("package", rule.packageName)
                put("state", rule.state)
                put("action", rule.action)
                put("label", rule.label)
                put("enabled", rule.enabled)
            })
        }
    }.toString()

    fun decodeForegroundRules(raw: String): List<ForegroundRule> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").trim()
                val action = obj.optString("action").trim()
                val state = obj.optString("state", APP_STATE_ENTER)
                    .takeIf { it == APP_STATE_ENTER || it == APP_STATE_EXIT }
                    ?: APP_STATE_ENTER
                if (pkg.isBlank() || action.isBlank() || action == "none") continue
                add(
                    ForegroundRule(
                        id = obj.optString("id").ifBlank { "foreground_$i" },
                        packageName = pkg,
                        state = state,
                        action = action,
                        label = obj.optString("label"),
                        enabled = obj.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encodeCustomKeys(rules: Collection<CustomKeyRule>): String = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("keyCode", rule.keyCode)
                put("trigger", rule.trigger)
                put("action", rule.action)
                put("label", rule.label)
                put("enabled", rule.enabled)
            })
        }
    }.toString()

    fun decodeCustomKeys(raw: String): List<CustomKeyRule> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val keyCode = obj.optInt("keyCode", -1)
                val trigger = obj.optString("trigger").trim()
                val action = obj.optString("action").trim()
                if (keyCode < 0 || trigger !in setOf("click", "double_click", "long_press") || action.isBlank() || action == "none") {
                    continue
                }
                add(
                    CustomKeyRule(
                        id = obj.optString("id").ifBlank { "key_$i" },
                        keyCode = keyCode,
                        trigger = trigger,
                        action = action,
                        label = obj.optString("label"),
                        enabled = obj.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
