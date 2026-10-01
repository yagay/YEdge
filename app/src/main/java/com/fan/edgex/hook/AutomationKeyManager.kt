package com.fan.edgex.hook

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.fan.edgex.config.AutomationConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge

/** Handles user-defined hardware keys beyond the built-in volume/power mappings. */
internal object AutomationKeyManager {
    private const val TAG = "EdgeX:CustomKey"
    private const val CACHE_TTL_MS = 2_000L
    private const val INJECTED_EVENT_FLAG = 0x04000000

    private val handler = Handler(Looper.getMainLooper())
    private var cachedAt = 0L
    private var cachedRules: Map<Int, List<AutomationConfig.CustomKeyRule>> = emptyMap()
    private val longPressTasks = mutableMapOf<Int, Runnable>()
    private val longPressed = mutableSetOf<Int>()
    private val lastUpAt = mutableMapOf<Int, Long>()
    private val pendingClicks = mutableMapOf<Int, Runnable>()
    private val seenEventKeys = LinkedHashSet<String>()

    fun handleKeyEvent(event: KeyEvent, context: Context, policyFlags: Int = 0): Boolean {
        // Never feed injected key actions back into a custom-key rule. This protects
        // input-key actions and OEM key remaps from recursive loops.
        if (policyFlags and INJECTED_EVENT_FLAG != 0) return false

        val rules = rulesFor(event.keyCode)
        if (rules.isEmpty()) return false

        // InputManagerService can surface the same physical KeyEvent through both
        // interceptKeyBeforeDispatching and filterInputEvent. Once a configured custom
        // key is handled, consume the duplicate path too instead of allowing the built-in
        // key state machine to see a second copy.
        val eventIdentity = "${event.keyCode}:${event.action}:${event.eventTime}:${event.repeatCount}"
        synchronized(seenEventKeys) {
            if (!seenEventKeys.add(eventIdentity)) return true
            while (seenEventKeys.size > 64) seenEventKeys.remove(seenEventKeys.first())
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(event, context, rules)
            KeyEvent.ACTION_UP -> handleUp(event, context, rules)
            else -> false
        }
    }

    fun reset() {
        longPressTasks.values.forEach(handler::removeCallbacks)
        pendingClicks.values.forEach(handler::removeCallbacks)
        longPressTasks.clear()
        pendingClicks.clear()
        longPressed.clear()
        lastUpAt.clear()
        synchronized(seenEventKeys) { seenEventKeys.clear() }
    }

    fun invalidateConfig() {
        cachedAt = 0L
    }

    private fun handleDown(
        event: KeyEvent,
        context: Context,
        rules: List<AutomationConfig.CustomKeyRule>,
    ): Boolean {
        if (event.repeatCount > 0) return true
        longPressTasks.remove(event.keyCode)?.let(handler::removeCallbacks)
        longPressed.remove(event.keyCode)

        val longRule = rules.firstOrNull { it.trigger == "long_press" }
        if (longRule != null) {
            val task = Runnable {
                longPressTasks.remove(event.keyCode)
                longPressed += event.keyCode
                dispatch(context, longRule.action, "key:${event.keyCode}:long_press")
            }
            longPressTasks[event.keyCode] = task
            handler.postDelayed(task, ViewConfiguration.getLongPressTimeout().toLong())
        }
        return true
    }

    private fun handleUp(
        event: KeyEvent,
        context: Context,
        rules: List<AutomationConfig.CustomKeyRule>,
    ): Boolean {
        longPressTasks.remove(event.keyCode)?.let(handler::removeCallbacks)
        if (longPressed.remove(event.keyCode)) {
            lastUpAt.remove(event.keyCode)
            pendingClicks.remove(event.keyCode)?.let(handler::removeCallbacks)
            return true
        }

        val clickRule = rules.firstOrNull { it.trigger == "click" }
        val doubleRule = rules.firstOrNull { it.trigger == "double_click" }
        if (doubleRule == null) {
            clickRule?.let { dispatch(context, it.action, "key:${event.keyCode}:click") }
            return true
        }

        val timeout = ViewConfiguration.getDoubleTapTimeout().toLong()
        val previousUp = lastUpAt[event.keyCode]
        if (previousUp != null && event.eventTime - previousUp in 0..timeout) {
            pendingClicks.remove(event.keyCode)?.let(handler::removeCallbacks)
            lastUpAt.remove(event.keyCode)
            dispatch(context, doubleRule.action, "key:${event.keyCode}:double_click")
            return true
        }

        lastUpAt[event.keyCode] = event.eventTime
        val task = Runnable {
            pendingClicks.remove(event.keyCode)
            lastUpAt.remove(event.keyCode)
            clickRule?.let { dispatch(context, it.action, "key:${event.keyCode}:click") }
        }
        pendingClicks[event.keyCode] = task
        handler.postDelayed(task, timeout)
        return true
    }

    private fun rulesFor(keyCode: Int): List<AutomationConfig.CustomKeyRule> {
        val now = System.currentTimeMillis()
        if (now - cachedAt > CACHE_TTL_MS) {
            val snapshot = HookConfigSnapshot.readFromHookFile()
            cachedRules = AutomationConfig.decodeCustomKeys(snapshot[AutomationConfig.CUSTOM_KEY_RULES].orEmpty())
                .asSequence()
                .filter { it.enabled }
                .groupBy { it.keyCode }
            cachedAt = now
        }
        return cachedRules[keyCode].orEmpty()
    }

    private fun dispatch(context: Context, action: String, source: String) {
        if (action.isBlank() || action == "none") return
        XposedBridge.log("$TAG $source -> $action")
        runCatching {
            context.sendBroadcast(Intent(HookConfigSnapshot.ACTION_EXECUTE_ACTION).apply {
                putExtra(HookConfigSnapshot.EXTRA_ACTION_CODE, action)
            })
        }.onFailure { XposedBridge.log("$TAG dispatch failed: ${it.message}") }
    }
}
