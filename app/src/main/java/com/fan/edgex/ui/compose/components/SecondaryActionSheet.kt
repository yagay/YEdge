package com.fan.edgex.ui.compose.components

import androidx.compose.runtime.Composable
import com.fan.edgex.R
import com.fan.edgex.config.MultiActionStore
import com.fan.edgex.config.putConfigsSync
import com.fan.edgex.ui.compose.screens.ConditionSheet
import com.fan.edgex.ui.compose.screens.MultiActionPickerSheet
import com.fan.edgex.ui.compose.screens.SubGestureSheet

enum class SecondaryType {
    AppPicker,
    FreezeApp,
    UnfreezeApp,
    MusicControl,
    FastScroll,
    ShellCommand,
    AppShortcut,
    SubGesture,
    Condition,
    MultiAction,
    Delay,
    InputKey,
    InputText,
    InputTap,
    InputSwipe,
    LaunchActivity,
    VariableSet,
    VariableToggle,
    Toast,
    TextToSpeech,
    Notification,
    ;

    companion object {
        fun fromCode(code: String): SecondaryType? = when (code) {
            "launch_app" -> AppPicker
            "freeze_app" -> FreezeApp
            "unfreeze_app" -> UnfreezeApp
            "music_control" -> MusicControl
            "fast_scroll" -> FastScroll
            "shell_command" -> ShellCommand
            "app_shortcut" -> AppShortcut
            "sub_gesture" -> SubGesture
            "condition" -> Condition
            "multi_action" -> MultiAction
            "delay" -> Delay
            "input_key" -> InputKey
            "input_text" -> InputText
            "input_tap" -> InputTap
            "input_swipe" -> InputSwipe
            "launch_activity" -> LaunchActivity
            "set_variable" -> VariableSet
            "toggle_variable" -> VariableToggle
            "show_toast" -> Toast
            "speak_text" -> TextToSpeech
            "post_notification" -> Notification
            else -> null
        }
    }
}

@Composable
fun SecondaryActionDispatcher(
    type: SecondaryType?,
    prefKey: String,
    title: String,
    excludedCodes: Set<String> = emptySet(),
    onCreateMultiAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    when (type) {
        SecondaryType.AppPicker -> {
            AppPickerSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { app ->
                    context.putConfigsSync(
                        prefKey to "launch_app:${app.packageName}",
                        "${prefKey}_label" to app.label,
                        "${prefKey}_title" to app.label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.FreezeApp,
        SecondaryType.UnfreezeApp -> {
            val freeze = type == SecondaryType.FreezeApp
            AppPickerSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { app ->
                    val command = if (freeze) {
                        "pm disable-user --user 0 ${app.packageName}"
                    } else {
                        "pm enable --user 0 ${app.packageName}"
                    }
                    val label = context.getString(
                        if (freeze) R.string.action_freeze_app else R.string.action_unfreeze_app,
                    ) + ": " + app.label
                    context.putConfigsSync(
                        prefKey to "shell:true:$command",
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to app.label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.MusicControl -> {
            MusicControlSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { code, label ->
                    context.putConfigsSync(
                        prefKey to "music_control:$code",
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to "",
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.FastScroll -> {
            FastScrollSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { code, label ->
                    context.putConfigsSync(
                        prefKey to "fast_scroll:$code",
                        "${prefKey}_label" to label,
                        "${prefKey}_title" to "",
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.ShellCommand -> {
            ShellCommandSheet(
                open = true,
                prefKey = prefKey,
                onDismiss = onDismiss,
                onSave = onSaved,
            )
        }

        SecondaryType.AppShortcut -> {
            AppShortcutPickerSheet(
                open = true,
                onDismiss = onDismiss,
                onPick = { shortcut ->
                    context.putConfigsSync(
                        prefKey to "app_shortcut:${shortcut.packageName}:${shortcut.shortcutId}",
                        "${prefKey}_label" to shortcut.label,
                        "${prefKey}_title" to shortcut.label,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.SubGesture -> {
            SubGestureSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                excludedCodes = excludedCodes,
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.Condition -> {
            ConditionSheet(
                open = true,
                prefKey = prefKey,
                title = title,
                excludedCodes = excludedCodes,
                onDismiss = onDismiss,
                onSaved = onSaved,
            )
        }

        SecondaryType.MultiAction -> {
            MultiActionPickerSheet(
                open = true,
                currentId = "",
                onCreate = onCreateMultiAction,
                onDismiss = onDismiss,
                onPick = { action ->
                    context.putConfigsSync(
                        prefKey to MultiActionStore.actionCode(action.id),
                        "${prefKey}_label" to action.name,
                        "${prefKey}_title" to action.name,
                    )
                    onSaved()
                },
            )
        }

        SecondaryType.Delay -> DelayActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.InputKey -> InputKeyActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.InputText -> InputTextActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.InputTap -> InputTapActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.InputSwipe -> InputSwipeActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.LaunchActivity -> LaunchActivityActionSheet(prefKey, onDismiss, onSaved)
        SecondaryType.VariableSet -> VariableSetActionSheet(prefKey, toggle = false, onDismiss = onDismiss, onSaved = onSaved)
        SecondaryType.VariableToggle -> VariableSetActionSheet(prefKey, toggle = true, onDismiss = onDismiss, onSaved = onSaved)
        SecondaryType.Toast -> FeedbackActionSheet(prefKey, "toast", R.string.action_show_toast, onDismiss, onSaved)
        SecondaryType.TextToSpeech -> FeedbackActionSheet(prefKey, "tts", R.string.action_speak_text, onDismiss, onSaved)
        SecondaryType.Notification -> FeedbackActionSheet(prefKey, "notification", R.string.action_post_notification, onDismiss, onSaved)
        null -> {}
    }
}
