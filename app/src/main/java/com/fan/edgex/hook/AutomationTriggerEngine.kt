package com.fan.edgex.hook

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.fan.edgex.config.AutomationConfig
import com.fan.edgex.config.HookConfigSnapshot
import de.robv.android.xposed.XposedBridge
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-frequency automation trigger engine hosted in system_server.
 *
 * Gesture/key actions remain the primary trigger path. This engine adds the missing
 * event/schedule/app-state trigger layer without introducing another privileged service.
 */
internal object AutomationTriggerEngine {
    private const val TAG = "EdgeX:Automation"
    private const val FOREGROUND_POLL_MS = 750L
    private const val CLOCK_POLL_MS = 15_000L
    private const val EVENT_DEBOUNCE_MS = 300L

    private val handler = Handler(Looper.getMainLooper())
    private val lastEventAt = ConcurrentHashMap<String, Long>()

    @Volatile private var context: Context? = null
    @Volatile private var initialized = false
    @Volatile private var lastForegroundPackage: String? = null
    @Volatile private var lastNetworkConnected: Boolean? = null
    @Volatile private var lastWifiConnected: Boolean? = null
    private var lastScheduleMinuteKey: String? = null

    fun initialize(ctx: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            context = ctx
            initialized = true
            registerSystemEvents(ctx)
            registerNetworkEvents(ctx)
            handler.post(foregroundPoll)
            handler.post(clockPoll)
            XposedBridge.log("$TAG initialized")
        }
    }

    fun onSystemUiState(statusBarVisible: Boolean, navigationBarVisible: Boolean, transient: Boolean) {
        val previous = SystemUiStateHolder.update(statusBarVisible, navigationBarVisible, transient)
        if (previous == null) return

        if (previous.statusBarVisible != statusBarVisible) {
            fire(
                if (statusBarVisible) AutomationConfig.EVENT_STATUS_BAR_SHOWN
                else AutomationConfig.EVENT_STATUS_BAR_HIDDEN,
            )
        }
        if (previous.navigationBarVisible != navigationBarVisible) {
            fire(
                if (navigationBarVisible) AutomationConfig.EVENT_NAV_BAR_SHOWN
                else AutomationConfig.EVENT_NAV_BAR_HIDDEN,
            )
        }

        val oldFullscreen = previous.fullscreen
        val newFullscreen = SystemUiStateHolder.current()?.fullscreen ?: false
        if (oldFullscreen != newFullscreen) {
            fire(
                if (newFullscreen) AutomationConfig.EVENT_FULLSCREEN_ENTER
                else AutomationConfig.EVENT_FULLSCREEN_EXIT,
            )
        }
    }

    fun fire(event: String) {
        val ctx = context ?: return
        val now = System.currentTimeMillis()
        val previous = lastEventAt[event] ?: 0L
        if (now - previous < EVENT_DEBOUNCE_MS) return
        lastEventAt[event] = now

        val snapshot = HookConfigSnapshot.readFromHookFile()
        if (snapshot[AutomationConfig.ENABLED] == "false") return
        val action = snapshot[AutomationConfig.eventAction(event)].orEmpty()
        dispatch(ctx, action, "event:$event")
    }

    private fun registerSystemEvents(ctx: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> fire(AutomationConfig.EVENT_BOOT_COMPLETED)
                    Intent.ACTION_SCREEN_ON -> fire(AutomationConfig.EVENT_SCREEN_ON)
                    Intent.ACTION_SCREEN_OFF -> {
                        AutomationKeyManager.reset()
                        fire(AutomationConfig.EVENT_SCREEN_OFF)
                    }
                    Intent.ACTION_USER_PRESENT -> fire(AutomationConfig.EVENT_USER_PRESENT)
                    Intent.ACTION_USER_UNLOCKED -> {
                        AutomationKeyManager.invalidateConfig()
                        fire(AutomationConfig.EVENT_USER_UNLOCKED)
                    }
                    Intent.ACTION_POWER_CONNECTED -> fire(AutomationConfig.EVENT_POWER_CONNECTED)
                    Intent.ACTION_POWER_DISCONNECTED -> fire(AutomationConfig.EVENT_POWER_DISCONNECTED)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BOOT_COMPLETED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
        }.onFailure { XposedBridge.log("$TAG system receiver failed: ${it.message}") }
    }

    private fun registerNetworkEvents(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        fun update(network: Network?) {
            val caps = network?.let(cm::getNetworkCapabilities)
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val wifi = connected && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            val oldConnected = lastNetworkConnected
            val oldWifi = lastWifiConnected
            lastNetworkConnected = connected
            lastWifiConnected = wifi

            if (oldConnected != null && oldConnected != connected) {
                fire(
                    if (connected) AutomationConfig.EVENT_NETWORK_CONNECTED
                    else AutomationConfig.EVENT_NETWORK_DISCONNECTED,
                )
            }
            if (oldWifi != null && oldWifi != wifi) {
                fire(
                    if (wifi) AutomationConfig.EVENT_WIFI_CONNECTED
                    else AutomationConfig.EVENT_WIFI_DISCONNECTED,
                )
            }
        }

        update(cm.activeNetwork)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = update(network)
                override fun onLost(network: Network) = update(cm.activeNetwork)
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = update(network)
            }, handler)
        }.onFailure { XposedBridge.log("$TAG network callback failed: ${it.message}") }
    }

    private val foregroundPoll = object : Runnable {
        override fun run() {
            try {
                val ctx = context
                if (ctx != null) {
                    val snapshot = HookConfigSnapshot.readFromHookFile()
                    val rules = AutomationConfig.decodeForegroundRules(snapshot[AutomationConfig.FOREGROUND_RULES].orEmpty())
                        .filter { it.enabled }
                    if (rules.isNotEmpty()) {
                        val am = ctx.getSystemService(ActivityManager::class.java)
                        @Suppress("DEPRECATION")
                        val current = am?.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName
                        if (!current.isNullOrBlank() && current != lastForegroundPackage) {
                            lastForegroundPackage = current
                            rules.filter { it.packageName == current }.forEach { rule ->
                                dispatch(ctx, rule.action, "foreground:${rule.packageName}")
                            }
                        }
                    } else {
                        lastForegroundPackage = null
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG foreground poll failed: ${t.message}")
            } finally {
                handler.postDelayed(this, FOREGROUND_POLL_MS)
            }
        }
    }

    private val clockPoll = object : Runnable {
        override fun run() {
            try {
                val ctx = context
                if (ctx != null) {
                    val snapshot = HookConfigSnapshot.readFromHookFile()
                    val rules = AutomationConfig.decodeSchedules(snapshot[AutomationConfig.SCHEDULE_RULES].orEmpty())
                        .filter { it.enabled }
                    if (rules.isNotEmpty()) {
                        val cal = Calendar.getInstance()
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
                        val minuteKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}-$hour-$minute"
                        if (minuteKey != lastScheduleMinuteKey) {
                            lastScheduleMinuteKey = minuteKey
                            rules.filter { rule ->
                                rule.hour == hour &&
                                    rule.minute == minute &&
                                    (rule.daysMask == 0 || (rule.daysMask and (1 shl dayIndex)) != 0)
                            }.forEach { rule -> dispatch(ctx, rule.action, "schedule:${rule.id}") }
                        }
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG clock poll failed: ${t.message}")
            } finally {
                handler.postDelayed(this, CLOCK_POLL_MS)
            }
        }
    }

    private fun dispatch(ctx: Context, action: String, source: String) {
        if (action.isBlank() || action == "none") return
        XposedBridge.log("$TAG dispatch source=$source action=$action")
        runCatching {
            ctx.sendBroadcast(Intent(HookConfigSnapshot.ACTION_EXECUTE_ACTION).apply {
                putExtra(HookConfigSnapshot.EXTRA_ACTION_CODE, action)
            })
        }.onFailure { XposedBridge.log("$TAG dispatch failed: ${it.message}") }
    }

    private object SystemUiStateHolder {
        data class State(
            val statusBarVisible: Boolean,
            val navigationBarVisible: Boolean,
            val transient: Boolean,
            val fullscreen: Boolean,
        )

        private var state: State? = null

        @Synchronized
        fun update(statusBarVisible: Boolean, navigationBarVisible: Boolean, transient: Boolean): State? {
            val old = state
            val effectiveStatus = if (transient && old?.fullscreen == true) false else statusBarVisible
            val effectiveNavigation = if (transient && old?.fullscreen == true) false else navigationBarVisible
            state = State(
                statusBarVisible = statusBarVisible,
                navigationBarVisible = navigationBarVisible,
                transient = transient,
                fullscreen = !effectiveStatus && !effectiveNavigation,
            )
            return old
        }

        @Synchronized
        fun current(): State? = state
    }
}
