package com.fan.edgex.hook

import android.view.WindowInsets
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/** Android 15/16 compatible status/navigation bar visibility trigger source. */
internal object AutomationSystemUiHooks {
    private const val TAG = "EdgeX:AutomationBars"

    @Volatile private var installed = false
    @Volatile private var statusVisible: Boolean? = null
    @Volatile private var navigationVisible: Boolean? = null

    fun install(classLoader: ClassLoader) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true

            val candidates = listOf(
                "com.android.server.wm.InsetsSourceProvider",
                "com.android.server.wm.WindowStateInsetsSourceProvider",
            )
            var hooked = false
            candidates.forEach { className ->
                runCatching {
                    val clazz = XposedHelpers.findClass(className, classLoader)
                    val result = XposedBridge.hookAllMethods(clazz, "updateVisibility", object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            updateFromProvider(param.thisObject)
                        }
                    })
                    if (result.isNotEmpty()) {
                        hooked = true
                        XposedBridge.log("$TAG hooked $className#updateVisibility (${result.size})")
                    }
                }
            }
            if (!hooked) {
                XposedBridge.log("$TAG no InsetsSourceProvider updateVisibility method found")
            }
        }
    }

    private fun updateFromProvider(provider: Any) {
        runCatching {
            val source = getField(provider, "mSource") ?: return
            val type = runCatching { XposedHelpers.callMethod(source, "getType") as Int }
                .recoverCatching { XposedHelpers.getIntField(source, "mType") }
                .getOrNull() ?: return
            val visible = runCatching { XposedHelpers.callMethod(source, "isVisible") as Boolean }
                .recoverCatching { XposedHelpers.getBooleanField(source, "mVisible") }
                .getOrNull() ?: return

            when (type) {
                WindowInsets.Type.statusBars() -> statusVisible = visible
                WindowInsets.Type.navigationBars() -> navigationVisible = visible
                else -> return
            }

            val status = statusVisible ?: return
            val navigation = navigationVisible ?: return
            AutomationTriggerEngine.onSystemUiState(
                statusBarVisible = status,
                navigationBarVisible = navigation,
                transient = isTransient(provider),
            )
        }.onFailure {
            XposedBridge.log("$TAG state read failed: ${it.message}")
        }
    }

    /**
     * Android 14+ transient system bars temporarily become visible while the app remains
     * fullscreen. Mirrors the patched Xposed Edge Pro logic but treats null targets as
     * non-transient to avoid false fullscreen transitions during app switches.
     */
    private fun isTransient(provider: Any): Boolean = runCatching {
        val controlTarget = getField(provider, "mControlTarget") ?: return false
        val displayContent = getField(provider, "mDisplayContent")
            ?: getField(controlTarget, "mDisplayContent")
            ?: return false
        val insetsPolicy = getField(displayContent, "mInsetsPolicy") ?: return false
        val transientTarget = getField(insetsPolicy, "mTransientControlTarget") ?: return false
        transientTarget === controlTarget
    }.getOrDefault(false)

    private fun getField(target: Any, name: String): Any? =
        runCatching { XposedHelpers.getObjectField(target, name) }.getOrNull()
}
