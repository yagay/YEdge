package com.fan.edgex.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.AutomationCodec

@Composable
fun ConditionParameterSheet(
    baseCode: String?,
    onDismiss: () -> Unit,
    onSave: (code: String, label: String) -> Unit,
) {
    val code = baseCode ?: return
    var first by remember(code) { mutableStateOf("") }
    var second by remember(code) { mutableStateOf("") }

    val title = when (code) {
        "battery_at_least" -> stringResource(R.string.cond_battery_at_least)
        "battery_at_most" -> stringResource(R.string.cond_battery_at_most)
        "time_between" -> stringResource(R.string.cond_time_between)
        "wifi_ssid" -> stringResource(R.string.cond_wifi_ssid)
        "variable_equals" -> stringResource(R.string.cond_variable_equals)
        "variable_exists" -> stringResource(R.string.cond_variable_exists)
        else -> code
    }

    val valid = when (code) {
        "battery_at_least", "battery_at_most" -> first.toIntOrNull() in 0..100
        "time_between" -> isValidTime(first) && isValidTime(second)
        "wifi_ssid" -> first.isNotBlank()
        "variable_equals" -> first.isNotBlank()
        "variable_exists" -> first.isNotBlank()
        else -> false
    }

    EdgeXBottomSheet(open = true, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (code) {
                "battery_at_least", "battery_at_most" -> {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit).take(3) },
                        label = { Text("0–100%") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                "time_between" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(first, { first = it.take(5) }, label = { Text("Start HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(second, { second = it.take(5) }, label = { Text("End HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                "wifi_ssid" -> {
                    OutlinedTextField(first, { first = it.take(128) }, label = { Text("SSID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                "variable_equals" -> {
                    OutlinedTextField(first, { first = it.take(64) }, label = { Text(stringResource(R.string.automation_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(second, { second = it.take(1000) }, label = { Text(stringResource(R.string.automation_value)) }, modifier = Modifier.fillMaxWidth())
                }
                "variable_exists" -> {
                    OutlinedTextField(first, { first = it.take(64) }, label = { Text(stringResource(R.string.automation_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(enabled = valid, onClick = {
                when (code) {
                    "battery_at_least" -> onSave("battery_at_least:${first.toInt()}", "Battery ≥ ${first.toInt()}%")
                    "battery_at_most" -> onSave("battery_at_most:${first.toInt()}", "Battery ≤ ${first.toInt()}%")
                    "time_between" -> onSave("time_between:${normalizeTime(first)}-${normalizeTime(second)}", "Time ${normalizeTime(first)}–${normalizeTime(second)}")
                    "wifi_ssid" -> onSave("wifi_ssid:${AutomationCodec.encode(first)}", "Wi-Fi: $first")
                    "variable_equals" -> onSave(
                        "variable_equals:${AutomationCodec.encode(first)}:${AutomationCodec.encode(second)}",
                        "$first = $second",
                    )
                    "variable_exists" -> onSave("variable_exists:${AutomationCodec.encode(first)}", "Variable exists: $first")
                }
            }) {
                Text(stringResource(R.string.automation_save))
            }
        }
    }
}

private fun isValidTime(value: String): Boolean {
    val parts = value.split(':')
    if (parts.size != 2) return false
    val hour = parts[0].toIntOrNull() ?: return false
    val minute = parts[1].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}

private fun normalizeTime(value: String): String {
    val parts = value.split(':')
    return "%02d:%02d".format(parts[0].toInt(), parts[1].toInt())
}
