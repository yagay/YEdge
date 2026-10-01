package com.fan.edgex.ui.compose.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.AutomationConfig
import com.fan.edgex.config.getConfigBool
import com.fan.edgex.config.getConfigString
import com.fan.edgex.config.putConfig
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.config.removeConfigs
import com.fan.edgex.ui.compose.components.ActionSelectionSheet
import com.fan.edgex.ui.compose.components.AppPickerSheet
import com.fan.edgex.ui.compose.components.EdgeXBottomSheet
import com.fan.edgex.ui.compose.components.EdgeXDivider
import com.fan.edgex.ui.compose.components.EdgeXIcon
import com.fan.edgex.ui.compose.components.EdgeXIcons
import com.fan.edgex.ui.compose.components.EdgeXListGroup
import com.fan.edgex.ui.compose.components.EdgeXRow
import com.fan.edgex.ui.compose.components.EdgeXSwitchRow
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.components.SecondaryActionDispatcher
import com.fan.edgex.ui.compose.components.SecondaryType
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

private data class EventDefinition(
    val code: String,
    @StringRes val labelRes: Int,
    val icon: Int,
)

private val eventDefinitions = listOf(
    EventDefinition(AutomationConfig.EVENT_BOOT_COMPLETED, R.string.event_boot_completed, EdgeXIcons.Restart),
    EventDefinition(AutomationConfig.EVENT_SCREEN_ON, R.string.event_screen_on, R.drawable.ic_power),
    EventDefinition(AutomationConfig.EVENT_SCREEN_OFF, R.string.event_screen_off, R.drawable.ic_power),
    EventDefinition(AutomationConfig.EVENT_USER_PRESENT, R.string.event_user_present, EdgeXIcons.Condition),
    EventDefinition(AutomationConfig.EVENT_USER_UNLOCKED, R.string.event_user_unlocked, EdgeXIcons.Condition),
    EventDefinition(AutomationConfig.EVENT_POWER_CONNECTED, R.string.event_power_connected, R.drawable.ic_power),
    EventDefinition(AutomationConfig.EVENT_POWER_DISCONNECTED, R.string.event_power_disconnected, R.drawable.ic_power),
    EventDefinition(AutomationConfig.EVENT_WIFI_CONNECTED, R.string.event_wifi_connected, EdgeXIcons.Wifi),
    EventDefinition(AutomationConfig.EVENT_WIFI_DISCONNECTED, R.string.event_wifi_disconnected, EdgeXIcons.Wifi),
    EventDefinition(AutomationConfig.EVENT_NETWORK_CONNECTED, R.string.event_network_connected, EdgeXIcons.Wifi),
    EventDefinition(AutomationConfig.EVENT_NETWORK_DISCONNECTED, R.string.event_network_disconnected, EdgeXIcons.Wifi),
    EventDefinition(AutomationConfig.EVENT_FULLSCREEN_ENTER, R.string.event_fullscreen_enter, EdgeXIcons.Gesture),
    EventDefinition(AutomationConfig.EVENT_FULLSCREEN_EXIT, R.string.event_fullscreen_exit, EdgeXIcons.Gesture),
    EventDefinition(AutomationConfig.EVENT_STATUS_BAR_SHOWN, R.string.event_status_bar_shown, EdgeXIcons.Condition),
    EventDefinition(AutomationConfig.EVENT_STATUS_BAR_HIDDEN, R.string.event_status_bar_hidden, EdgeXIcons.Condition),
    EventDefinition(AutomationConfig.EVENT_NAV_BAR_SHOWN, R.string.event_nav_bar_shown, EdgeXIcons.Condition),
    EventDefinition(AutomationConfig.EVENT_NAV_BAR_HIDDEN, R.string.event_nav_bar_hidden, EdgeXIcons.Condition),
)

private sealed interface PendingTarget {
    val prefKey: String
    val title: String

    data class Event(override val prefKey: String, override val title: String) : PendingTarget
    data class Schedule(
        override val prefKey: String,
        override val title: String,
        val hour: Int,
        val minute: Int,
        val daysMask: Int,
    ) : PendingTarget
    data class Foreground(
        override val prefKey: String,
        override val title: String,
        val packageName: String,
    ) : PendingTarget
    data class CustomKey(
        override val prefKey: String,
        override val title: String,
        val keyCode: Int,
        val trigger: String,
    ) : PendingTarget
}

@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    showToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEdgeXColors.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var showScheduleEditor by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showKeyEditor by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<PendingTarget?>(null) }
    var pendingSecondary by remember { mutableStateOf<SecondaryType?>(null) }
    var showActionPicker by remember { mutableStateOf(false) }

    val enabled = context.getConfigBool(AutomationConfig.ENABLED, true)
    val schedules = remember(refreshTick) {
        AutomationConfig.decodeSchedules(context.getConfigString(AutomationConfig.SCHEDULE_RULES))
    }
    val foregroundRules = remember(refreshTick) {
        AutomationConfig.decodeForegroundRules(context.getConfigString(AutomationConfig.FOREGROUND_RULES))
    }
    val customKeys = remember(refreshTick) {
        AutomationConfig.decodeCustomKeys(context.getConfigString(AutomationConfig.CUSTOM_KEY_RULES))
    }

    fun nextTempKey(): String = "automation_tmp_${System.currentTimeMillis()}"

    fun completePending(action: String, label: String) {
        val target = pendingTarget ?: return
        when (target) {
            is PendingTarget.Event -> {
                context.putConfigsSync(
                    target.prefKey to action,
                    "${target.prefKey}_label" to label,
                )
            }
            is PendingTarget.Schedule -> {
                val updated = schedules + AutomationConfig.ScheduleRule(
                    id = System.currentTimeMillis().toString(),
                    hour = target.hour,
                    minute = target.minute,
                    daysMask = target.daysMask,
                    action = action,
                    label = label,
                )
                context.putConfig(AutomationConfig.SCHEDULE_RULES, AutomationConfig.encodeSchedules(updated))
                context.removeConfigs(target.prefKey, "${target.prefKey}_label", "${target.prefKey}_title")
            }
            is PendingTarget.Foreground -> {
                val updated = foregroundRules + AutomationConfig.ForegroundRule(
                    id = System.currentTimeMillis().toString(),
                    packageName = target.packageName,
                    action = action,
                    label = label,
                )
                context.putConfig(AutomationConfig.FOREGROUND_RULES, AutomationConfig.encodeForegroundRules(updated))
                context.removeConfigs(target.prefKey, "${target.prefKey}_label", "${target.prefKey}_title")
            }
            is PendingTarget.CustomKey -> {
                val updated = customKeys + AutomationConfig.CustomKeyRule(
                    id = System.currentTimeMillis().toString(),
                    keyCode = target.keyCode,
                    trigger = target.trigger,
                    action = action,
                    label = label,
                )
                context.putConfig(AutomationConfig.CUSTOM_KEY_RULES, AutomationConfig.encodeCustomKeys(updated))
                context.removeConfigs(target.prefKey, "${target.prefKey}_label", "${target.prefKey}_title")
            }
        }
        pendingTarget = null
        pendingSecondary = null
        showActionPicker = false
        refreshTick++
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EdgeXTopBar(title = stringResource(R.string.menu_automation), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EdgeXListGroup {
                EdgeXSwitchRow(
                    title = stringResource(R.string.automation_enabled),
                    checked = enabled,
                    onCheckedChange = {
                        context.putConfig(AutomationConfig.ENABLED, it)
                        refreshTick++
                    },
                    icon = EdgeXIcons.Condition,
                )
            }

            SectionTitle(stringResource(R.string.automation_events))
            EdgeXListGroup {
                eventDefinitions.forEachIndexed { index, event ->
                    val prefKey = AutomationConfig.eventAction(event.code)
                    val subtitle = context.getConfigString("${prefKey}_label", context.getString(R.string.action_none))
                    EdgeXRow(
                        title = stringResource(event.labelRes),
                        subtitle = subtitle,
                        icon = event.icon,
                        onClick = {
                            pendingTarget = PendingTarget.Event(prefKey, context.getString(event.labelRes))
                            pendingSecondary = null
                            showActionPicker = true
                        },
                    ) {
                        EdgeXIcon(EdgeXIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceDim)
                    }
                    if (index != eventDefinitions.lastIndex) EdgeXDivider()
                }
            }

            SectionHeader(
                title = stringResource(R.string.automation_schedules),
                onAdd = { showScheduleEditor = true },
            )
            EdgeXListGroup {
                if (schedules.isEmpty()) {
                    EdgeXRow(title = stringResource(R.string.action_none), icon = EdgeXIcons.Multi)
                } else {
                    schedules.forEachIndexed { index, rule ->
                        EdgeXRow(
                            title = "%02d:%02d · %s".format(rule.hour, rule.minute, daysSummary(rule.daysMask)),
                            subtitle = rule.label.ifBlank { rule.action },
                            icon = EdgeXIcons.Multi,
                        ) {
                            DeleteText {
                                context.putConfig(
                                    AutomationConfig.SCHEDULE_RULES,
                                    AutomationConfig.encodeSchedules(schedules.filterNot { it.id == rule.id }),
                                )
                                refreshTick++
                            }
                        }
                        if (index != schedules.lastIndex) EdgeXDivider()
                    }
                }
            }

            SectionHeader(
                title = stringResource(R.string.automation_foreground_apps),
                onAdd = { showAppPicker = true },
            )
            EdgeXListGroup {
                if (foregroundRules.isEmpty()) {
                    EdgeXRow(title = stringResource(R.string.action_none), icon = EdgeXIcons.LaunchApp)
                } else {
                    foregroundRules.forEachIndexed { index, rule ->
                        val appLabel = runCatching {
                            val info = context.packageManager.getApplicationInfo(rule.packageName, 0)
                            context.packageManager.getApplicationLabel(info).toString()
                        }.getOrDefault(rule.packageName)
                        val stateLabel = if (rule.state == AutomationConfig.APP_STATE_EXIT) "Exit" else "Enter"
                        EdgeXRow(
                            title = "$appLabel · $stateLabel",
                            subtitle = rule.label.ifBlank { rule.action },
                            icon = EdgeXIcons.LaunchApp,
                            onClick = {
                                val nextState = if (rule.state == AutomationConfig.APP_STATE_ENTER) {
                                    AutomationConfig.APP_STATE_EXIT
                                } else {
                                    AutomationConfig.APP_STATE_ENTER
                                }
                                context.putConfig(
                                    AutomationConfig.FOREGROUND_RULES,
                                    AutomationConfig.encodeForegroundRules(
                                        foregroundRules.map { existing ->
                                            if (existing.id == rule.id) existing.copy(state = nextState) else existing
                                        },
                                    ),
                                )
                                refreshTick++
                            },
                        ) {
                            DeleteText {
                                context.putConfig(
                                    AutomationConfig.FOREGROUND_RULES,
                                    AutomationConfig.encodeForegroundRules(foregroundRules.filterNot { it.id == rule.id }),
                                )
                                refreshTick++
                            }
                        }
                        if (index != foregroundRules.lastIndex) EdgeXDivider()
                    }
                }
            }

            SectionHeader(
                title = stringResource(R.string.automation_custom_keys),
                onAdd = { showKeyEditor = true },
            )
            EdgeXListGroup {
                if (customKeys.isEmpty()) {
                    EdgeXRow(title = stringResource(R.string.action_none), icon = EdgeXIcons.Keys)
                } else {
                    customKeys.forEachIndexed { index, rule ->
                        EdgeXRow(
                            title = "Key ${rule.keyCode} · ${triggerLabel(rule.trigger)}",
                            subtitle = rule.label.ifBlank { rule.action },
                            icon = EdgeXIcons.Keys,
                        ) {
                            DeleteText {
                                context.putConfig(
                                    AutomationConfig.CUSTOM_KEY_RULES,
                                    AutomationConfig.encodeCustomKeys(customKeys.filterNot { it.id == rule.id }),
                                )
                                refreshTick++
                            }
                        }
                        if (index != customKeys.lastIndex) EdgeXDivider()
                    }
                }
            }
        }
    }

    ScheduleEditorSheet(
        open = showScheduleEditor,
        onDismiss = { showScheduleEditor = false },
        onSave = { hour, minute, daysMask ->
            showScheduleEditor = false
            val temp = nextTempKey()
            pendingTarget = PendingTarget.Schedule(temp, "Schedule", hour, minute, daysMask)
            showActionPicker = true
        },
    )

    AppPickerSheet(
        open = showAppPicker,
        onDismiss = { showAppPicker = false },
        onPick = { app ->
            showAppPicker = false
            val temp = nextTempKey()
            pendingTarget = PendingTarget.Foreground(temp, app.label, app.packageName)
            showActionPicker = true
        },
    )

    CustomKeyEditorSheet(
        open = showKeyEditor,
        onDismiss = { showKeyEditor = false },
        onSave = { keyCode, trigger ->
            showKeyEditor = false
            val temp = nextTempKey()
            pendingTarget = PendingTarget.CustomKey(temp, "Key $keyCode", keyCode, trigger)
            showActionPicker = true
        },
    )

    val target = pendingTarget
    if (target != null && showActionPicker && pendingSecondary == null) {
        ActionSelectionSheet(
            open = true,
            title = target.title,
            onDismiss = {
                if (target !is PendingTarget.Event) {
                    context.removeConfigs(target.prefKey, "${target.prefKey}_label", "${target.prefKey}_title")
                }
                pendingTarget = null
                showActionPicker = false
            },
            excludedCodes = emptySet(),
            onSelect = { action ->
                if (action.needsSecondary) {
                    pendingSecondary = SecondaryType.fromCode(action.code)
                    showActionPicker = false
                } else {
                    completePending(action.code, context.getString(action.labelRes))
                }
            },
        )
    }

    val secondary = pendingSecondary
    if (target != null && secondary != null) {
        SecondaryActionDispatcher(
            type = secondary,
            prefKey = target.prefKey,
            title = target.title,
            onDismiss = {
                if (target !is PendingTarget.Event) {
                    context.removeConfigs(target.prefKey, "${target.prefKey}_label", "${target.prefKey}_title")
                }
                pendingSecondary = null
                pendingTarget = null
            },
            onSaved = {
                val code = context.getConfigString(target.prefKey)
                val label = context.getConfigString("${target.prefKey}_label", code)
                if (target is PendingTarget.Event) {
                    pendingSecondary = null
                    pendingTarget = null
                    refreshTick++
                } else if (code.isNotBlank() && code != "none") {
                    completePending(code, label)
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = LocalEdgeXColors.current.onSurfaceDim)
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = LocalEdgeXColors.current.onSurfaceDim)
        Text(
            stringResource(R.string.automation_add),
            color = LocalEdgeXColors.current.accent,
            modifier = Modifier.clickable(onClick = onAdd).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun DeleteText(onDelete: () -> Unit) {
    Text("Delete", color = LocalEdgeXColors.current.accent, modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
}

@Composable
private fun ScheduleEditorSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, daysMask: Int) -> Unit,
) {
    if (!open) return
    var time by remember { mutableStateOf("08:00") }
    var daysMask by remember { mutableIntStateOf(0) }
    val parsed = parseTime(time)
    EdgeXBottomSheet(open = true, title = stringResource(R.string.automation_schedules), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = time,
            onValueChange = { time = it.take(5) },
            label = { Text(stringResource(R.string.automation_time)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("Days (none selected = every day)", modifier = Modifier.padding(top = 10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, label ->
                val selected = daysMask and (1 shl index) != 0
                FilterChip(
                    selected = selected,
                    onClick = { daysMask = if (selected) daysMask and (1 shl index).inv() else daysMask or (1 shl index) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(enabled = parsed != null, onClick = {
                val value = parsed ?: return@Button
                onSave(value.first, value.second, daysMask)
            }) { Text(stringResource(R.string.automation_save)) }
        }
    }
}

@Composable
private fun CustomKeyEditorSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    onSave: (keyCode: Int, trigger: String) -> Unit,
) {
    if (!open) return
    var keyCodeText by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("click") }
    val keyCode = keyCodeText.toIntOrNull()?.takeIf { it in 0..1000 }
    EdgeXBottomSheet(open = true, title = stringResource(R.string.automation_custom_keys), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = keyCodeText,
            onValueChange = { keyCodeText = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.automation_keycode)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("click", "double_click", "long_press").forEach { value ->
                FilterChip(
                    selected = trigger == value,
                    onClick = { trigger = value },
                    label = { Text(triggerLabel(value)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(enabled = keyCode != null, onClick = { keyCode?.let { onSave(it, trigger) } }) {
                Text(stringResource(R.string.automation_save))
            }
        }
    }
}

private fun parseTime(value: String): Pair<Int, Int>? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}

private fun daysSummary(mask: Int): String {
    if (mask == 0) return "Every day"
    if (mask == 0b0111110) return "Weekdays"
    if (mask == 0b1000001) return "Weekends"
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return labels.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.joinToString(",")
}

private fun triggerLabel(trigger: String): String = when (trigger) {
    "double_click" -> "Double"
    "long_press" -> "Long"
    else -> "Click"
}
