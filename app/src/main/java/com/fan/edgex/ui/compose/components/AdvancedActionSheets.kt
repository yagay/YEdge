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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fan.edgex.R
import com.fan.edgex.config.AutomationCodec
import com.fan.edgex.config.putConfigsSync

@Composable
private fun SaveRow(enabled: Boolean, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onSave, enabled = enabled) {
            Text(stringResource(R.string.automation_save))
        }
    }
}

private fun saveAction(
    context: android.content.Context,
    prefKey: String,
    code: String,
    label: String,
): Boolean = context.putConfigsSync(
    prefKey to code,
    "${prefKey}_label" to label,
    "${prefKey}_title" to label,
)

@Composable
fun DelayActionSheet(
    prefKey: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var value by remember { mutableStateOf("1000") }
    val millis = value.toLongOrNull()?.coerceIn(0L, 600_000L)
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_delay), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.automation_duration_ms)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SaveRow(millis != null) {
            val delay = millis ?: return@SaveRow
            if (saveAction(context, prefKey, "delay:$delay", "Delay ${delay}ms")) onSaved()
        }
    }
}

@Composable
fun InputKeyActionSheet(prefKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var keyCode by remember { mutableStateOf("") }
    val code = keyCode.toIntOrNull()?.takeIf { it in 0..1000 }
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_input_key), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = keyCode,
            onValueChange = { keyCode = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.automation_keycode)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SaveRow(code != null) {
            val value = code ?: return@SaveRow
            if (saveAction(context, prefKey, "shell:true:input keyevent $value", "Input key $value")) onSaved()
        }
    }
}

@Composable
fun InputTextActionSheet(prefKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_input_text), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(1000) },
            label = { Text(stringResource(R.string.automation_value)) },
            modifier = Modifier.fillMaxWidth(),
        )
        SaveRow(text.isNotEmpty()) {
            val inputText = text.replace(" ", "%s")
            val cmd = "input text ${AutomationCodec.shellQuote(inputText)}"
            if (saveAction(context, prefKey, "shell:true:$cmd", "Input text")) onSaved()
        }
    }
}

@Composable
fun InputTapActionSheet(prefKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    val valid = x.toIntOrNull() != null && y.toIntOrNull() != null
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_input_tap), onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(x, { x = it.filter(Char::isDigit).take(5) }, label = { Text("X") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(y, { y = it.filter(Char::isDigit).take(5) }, label = { Text("Y") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        SaveRow(valid) {
            val xi = x.toIntOrNull() ?: return@SaveRow
            val yi = y.toIntOrNull() ?: return@SaveRow
            if (saveAction(context, prefKey, "shell:true:input tap $xi $yi", "Tap $xi,$yi")) onSaved()
        }
    }
}

@Composable
fun InputSwipeActionSheet(prefKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var x1 by remember { mutableStateOf("") }
    var y1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }
    var y2 by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("300") }
    val valid = listOf(x1, y1, x2, y2, duration).all { it.toIntOrNull() != null }
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_input_swipe), onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(x1, { x1 = it.filter(Char::isDigit).take(5) }, label = { Text("X1") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(y1, { y1 = it.filter(Char::isDigit).take(5) }, label = { Text("Y1") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(x2, { x2 = it.filter(Char::isDigit).take(5) }, label = { Text("X2") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(y2, { y2 = it.filter(Char::isDigit).take(5) }, label = { Text("Y2") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.automation_duration_ms)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        SaveRow(valid) {
            val values = listOf(x1, y1, x2, y2, duration).map { it.toIntOrNull() ?: return@SaveRow }
            val ms = values[4].coerceIn(1, 600_000)
            val cmd = "input swipe ${values[0]} ${values[1]} ${values[2]} ${values[3]} $ms"
            if (saveAction(context, prefKey, "shell:true:$cmd", "Swipe ${values[0]},${values[1]} → ${values[2]},${values[3]}")) onSaved()
        }
    }
}

@Composable
fun LaunchActivityActionSheet(prefKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var component by remember { mutableStateOf("") }
    val normalized = component.trim().takeIf { '/' in it && it.matches(Regex("[A-Za-z0-9_.$/-]+")) }
    EdgeXBottomSheet(open = true, title = stringResource(R.string.action_launch_activity), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = component,
            onValueChange = { component = it.take(250) },
            label = { Text(stringResource(R.string.automation_component)) },
            placeholder = { Text("com.example/.MainActivity") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SaveRow(normalized != null) {
            val value = normalized ?: return@SaveRow
            if (saveAction(context, prefKey, "shell:true:am start -n ${AutomationCodec.shellQuote(value)}", "Launch $value")) onSaved()
        }
    }
}

@Composable
fun VariableSetActionSheet(prefKey: String, toggle: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val safeName = AutomationCodec.variableName(name)
    EdgeXBottomSheet(
        open = true,
        title = stringResource(if (toggle) R.string.action_toggle_variable else R.string.action_set_variable),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(64) }, label = { Text(stringResource(R.string.automation_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (!toggle) {
                OutlinedTextField(value, { value = it.take(1000) }, label = { Text(stringResource(R.string.automation_value)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        SaveRow(name.isNotBlank()) {
            val dir = "/data/system/edgex/vars"
            val command = if (toggle) {
                "mkdir -p $dir; f=$dir/$safeName; v=\$(cat \"\$f\" 2>/dev/null); if [ \"\$v\" = true ]; then printf false > \"\$f\"; else printf true > \"\$f\"; fi"
                    .replace("\\$", "$")
            } else {
                "mkdir -p $dir; printf %s ${AutomationCodec.shellQuote(value)} > $dir/$safeName"
            }
            val label = if (toggle) "Toggle variable $safeName" else "Set $safeName = $value"
            if (saveAction(context, prefKey, "shell:true:$command", label)) onSaved()
        }
    }
}

@Composable
fun FeedbackActionSheet(
    prefKey: String,
    mode: String,
    titleRes: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    EdgeXBottomSheet(open = true, title = stringResource(titleRes), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(4000) },
            label = { Text(stringResource(R.string.automation_message)) },
            modifier = Modifier.fillMaxWidth(),
        )
        SaveRow(text.isNotBlank()) {
            val command = "am broadcast -a com.fan.edgex.AUTOMATION_FEEDBACK -n com.fan.edgex/.automation.AutomationFeedbackReceiver --es mode ${AutomationCodec.shellQuote(mode)} --es text ${AutomationCodec.shellQuote(text)}"
            val label = when (mode) {
                "tts" -> "Speak text"
                "notification" -> "Post notification"
                else -> "Show toast"
            }
            if (saveAction(context, prefKey, "shell:true:$command", label)) onSaved()
        }
    }
}
