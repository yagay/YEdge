package com.fan.edgex.hook

import android.app.ActivityManager
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import com.fan.edgex.config.AutomationCodec
import com.fan.edgex.config.ConditionStore
import com.fan.edgex.config.ForegroundAppConditionConfig
import java.io.File
import java.time.LocalTime

internal object ConditionEvaluator {

    fun evaluate(
        conditionCode: String,
        context: Context,
        foregroundAppConfig: ForegroundAppConditionConfig? = null,
    ): Boolean = try {
        when {
            conditionCode == "auto_brightness" -> isAutoBrightnessOn(context)
            conditionCode == "auto_rotate" -> isAutoRotateOn(context)
            conditionCode == "wifi_enabled" -> isWifiEnabled(context)
            conditionCode == "mobile_data" -> isMobileDataEnabled(context)
            conditionCode == "location" -> isLocationEnabled(context)
            conditionCode == "bluetooth" -> isBluetoothEnabled(context)
            conditionCode == "nfc" -> isNfcEnabled(context)
            conditionCode == "power_connected" -> isPowerConnected(context)
            conditionCode == "wifi_connected" -> isWifiConnected(context)
            conditionCode == "network_connected" -> isNetworkConnected(context)
            conditionCode == "media_playing" -> isMediaPlaying(context)
            conditionCode == "screen_portrait" -> isPortrait(context)
            conditionCode == "screen_landscape" -> isLandscape(context)
            conditionCode == "screen_on" -> isScreenOn(context)
            conditionCode == "keyguard_locked" -> isKeyguardLocked(context)
            conditionCode == "power_saver" -> isPowerSaver(context)
            conditionCode == "airplane_mode" -> isAirplaneMode(context)
            conditionCode == "headset_connected" -> isHeadsetConnected(context)
            conditionCode == ConditionStore.FOREGROUND_APP -> isForegroundAppMatch(context, foregroundAppConfig)
            conditionCode.startsWith("battery_at_least:") ->
                evaluateBatteryThreshold(context, conditionCode.substringAfter(':'), atLeast = true)
            conditionCode.startsWith("battery_at_most:") ->
                evaluateBatteryThreshold(context, conditionCode.substringAfter(':'), atLeast = false)
            conditionCode.startsWith("time_between:") -> isWithinTimeRange(conditionCode.substringAfter(':'))
            conditionCode.startsWith("wifi_ssid:") -> currentSsid(context) == AutomationCodec.decode(conditionCode.substringAfter(':'))
            conditionCode.startsWith("variable_equals:") -> variableEquals(conditionCode)
            conditionCode.startsWith("variable_exists:") -> variableExists(conditionCode)
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    private fun evaluateBatteryThreshold(context: Context, raw: String, atLeast: Boolean): Boolean {
        val threshold = raw.toIntOrNull()?.takeIf { it in 0..100 } ?: return false
        val level = batteryLevel(context)
        if (level < 0) return false
        return if (atLeast) level >= threshold else level <= threshold
    }

    private fun isAutoBrightnessOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun isAutoRotateOn(context: Context) =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun isWifiEnabled(context: Context): Boolean {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wm.isWifiEnabled
    }

    private fun isMobileDataEnabled(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return tm.dataState == TelephonyManager.DATA_CONNECTED
    }

    private fun isLocationEnabled(context: Context) =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) !=
            Settings.Secure.LOCATION_MODE_OFF

    private fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return bm.adapter?.state == BluetoothAdapter.STATE_ON
    }

    private fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    private fun isPowerConnected(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return bm.isCharging
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isMediaPlaying(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.isMusicActive
    }

    private fun isPortrait(context: Context) =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun isLandscape(context: Context) =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun isScreenOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun isKeyguardLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun isPowerSaver(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    private fun isAirplaneMode(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun isHeadsetConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun batteryLevel(context: Context): Int {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return wm.connectionInfo?.ssid
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
    }

    private fun isWithinTimeRange(raw: String): Boolean {
        val parts = raw.split('-', limit = 2)
        if (parts.size != 2) return false
        val start = parseTime(parts[0]) ?: return false
        val end = parseTime(parts[1]) ?: return false
        val now = LocalTime.now()
        return if (start <= end) {
            !now.isBefore(start) && !now.isAfter(end)
        } else {
            !now.isBefore(start) || !now.isAfter(end)
        }
    }

    private fun parseTime(raw: String): LocalTime? {
        val normalized = raw.trim().replace(":", "")
        if (normalized.length !in 3..4) return null
        val padded = normalized.padStart(4, '0')
        val hour = padded.substring(0, 2).toIntOrNull() ?: return null
        val minute = padded.substring(2, 4).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun variableEquals(code: String): Boolean {
        val payload = code.removePrefix("variable_equals:")
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return false
        val name = AutomationCodec.variableName(AutomationCodec.decode(parts[0]))
        val expected = AutomationCodec.decode(parts[1])
        return readVariable(name) == expected
    }

    private fun variableExists(code: String): Boolean {
        val name = AutomationCodec.variableName(
            AutomationCodec.decode(code.removePrefix("variable_exists:")),
        )
        return variableFile(name).isFile
    }

    private fun readVariable(name: String): String? =
        runCatching { variableFile(name).takeIf(File::isFile)?.readText()?.trimEnd() }.getOrNull()

    private fun variableFile(name: String): File =
        File("/data/system/edgex/vars", name)

    @Suppress("DEPRECATION")
    private fun isForegroundAppMatch(
        context: Context,
        config: ForegroundAppConditionConfig?,
    ): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
        val foregroundPackage = activityManager.getRunningTasks(1)
            .firstOrNull()
            ?.topActivity
            ?.packageName
        return matchesForegroundApp(config, foregroundPackage)
    }

    internal fun matchesForegroundApp(
        config: ForegroundAppConditionConfig?,
        foregroundPackage: String?,
    ): Boolean {
        if (config == null || foregroundPackage.isNullOrBlank() || config.packageNames.isEmpty()) return false
        return foregroundPackage in config.packageNames
    }
}
