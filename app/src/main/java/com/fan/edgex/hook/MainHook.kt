package com.fan.edgex.hook

import android.os.Handler
import android.os.Looper
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.fan.edgex.config.ModuleActivationState
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        ModuleRes.init(startupParam.modulePath)
        ScrollHook.install()
    }

    companion object {
        private const val TAG = "EdgeX"

        /**
         * Check if the current call was initiated by our own code.
         * This is used to detect injected events and skip processing them.
         * Following Xposed Edge Pro's approach.
         */
        fun isCalledByUs(): Boolean {
            val stackTrace = Throwable().stackTrace
            for (i in 2 until stackTrace.size) {
                if (stackTrace[i].className.startsWith("com.fan.edgex")) {
                    return true
                }
            }
            return false
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> {
                PremiumPluginLoader.tryLoad()
                AutomationSystemUiHooks.install(lpparam.classLoader)
                hookInputManager(lpparam)
            }
        }
    }

    private fun notifyModuleLoaded(context: android.content.Context) {
        runCatching {
            context.sendBroadcast(ModuleActivationState.responseIntent(System.currentTimeMillis()))
        }.onFailure {
            XposedBridge.log("$TAG: Failed to notify module loaded: ${it.message}")
        }
    }

    private fun handleKeyEvent(
        event: KeyEvent,
        context: android.content.Context,
        param: XC_MethodHook.MethodHookParam,
        policyFlags: Int,
    ): Boolean {
        if (AutomationKeyManager.handleKeyEvent(event, context, policyFlags)) return true
        return GestureManager.handleKeyEvent(event, context, param, policyFlags)
    }

    /**
     * Hook InputManagerService.filterInputEvent in system_server to intercept touch and
     * hardware key events at the input pipeline level.
     */
    private fun hookInputManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val inputManagerService = XposedHelpers.findClass(
                "com.android.server.input.InputManagerService", lpparam.classLoader
            )

            try {
                XposedHelpers.findAndHookMethod(
                    inputManagerService, "interceptKeyBeforeDispatching",
                    "android.os.IBinder",
                    KeyEvent::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (isCalledByUs()) return

                            val keyEvent = param.args[1] as KeyEvent
                            val policyFlags = (param.args.getOrNull(2) as? Int) ?: 0
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as android.content.Context
                            if (handleKeyEvent(keyEvent, context, param, policyFlags)) {
                                param.result = -1L
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: interceptKeyBeforeDispatching hook failed: ${t.message}")
            }

            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isCalledByUs()) return

                    val event = param.args[0] as InputEvent
                    val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                        as android.content.Context

                    when (event) {
                        is MotionEvent -> {
                            if (GestureManager.handleMotionEvent(event, context)) {
                                param.setResult(false)
                            }
                        }
                        is KeyEvent -> {
                            val policyFlags = if (param.args.size > 1 && param.args[1] is Int) {
                                param.args[1] as Int
                            } else {
                                0
                            }
                            if (handleKeyEvent(event, context, param, policyFlags)) {
                                param.setResult(false)
                            }
                        }
                    }
                }
            }

            var hooked = false

            if (!hooked) {
                try {
                    XposedHelpers.findAndHookMethod(
                        inputManagerService, "filterInputEvent",
                        InputEvent::class.java, Int::class.javaPrimitiveType, hook
                    )
                    hooked = true
                } catch (_: Throwable) {
                }
            }

            if (!hooked) {
                try {
                    XposedHelpers.findAndHookMethod(
                        inputManagerService, "filterInputEvent",
                        InputEvent::class.java, hook
                    )
                    hooked = true
                } catch (_: Throwable) {
                }
            }

            if (!hooked) {
                for (m: Method in inputManagerService.declaredMethods) {
                    if (m.name == "filterInputEvent") {
                        try {
                            XposedBridge.hookMethod(m, hook)
                            hooked = true
                            break
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Reflection hook failed: ${t.message}")
                        }
                    }
                }
            }

            if (!hooked) {
                XposedBridge.log("$TAG: ERROR - Failed to hook filterInputEvent with any method signature")
            }

            enableInputFilter(inputManagerService, lpparam.classLoader)
            UniversalCopyManager.installHooks(lpparam.classLoader)
            ClipboardHook.installHook(lpparam.classLoader)

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Error during InputManagerService hook: ${t.message}")
        }
    }

    /**
     * Enable InputFilter so that the native InputDispatcher calls filterInputEvent.
     *
     * Android 16+: NativeInputManagerService$NativeImpl.setInputFilterEnabled(boolean)
     * Legacy:      InputManagerService.nativeSetInputFilterEnabled(long, boolean)
     */
    private fun enableInputFilter(inputManagerService: Class<*>, classLoader: ClassLoader) {
        var mNativeInstance: Any? = null
        var inputManagerServiceInstance: Any? = null

        try {
            XposedHelpers.findAndHookMethod(
                inputManagerService, "setInputFilter",
                "android.view.IInputFilter",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == null) {
                            registerFakeInputFilter(param.thisObject, inputManagerService.classLoader!!)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook setInputFilter: ${t.message}")
        }

        try {
            val nativeImplClass = XposedHelpers.findClass(
                "com.android.server.input.NativeInputManagerService\$NativeImpl",
                classLoader
            )

            XposedHelpers.findAndHookMethod(nativeImplClass, "setInputFilterEnabled",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                })

            XposedHelpers.findAndHookConstructor(
                inputManagerService,
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        inputManagerServiceInstance = param.thisObject
                        mNativeInstance = XposedHelpers.getObjectField(param.thisObject, "mNative")
                    }
                })

            XposedHelpers.findAndHookMethod(inputManagerService, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                                as android.content.Context
                            GestureManager.initSystemServer(context)
                            AutomationTriggerEngine.initialize(context)
                            PremiumPluginLoader.verifyDeviceBinding(context)
                            notifyModuleLoaded(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to initialize EdgeX runtime in start(): ${t.message}")
                        }

                        try {
                            val native = mNativeInstance
                            if (native != null) {
                                XposedHelpers.callMethod(native, "setInputFilterEnabled", true)
                            } else {
                                XposedBridge.log("$TAG: mNative is null at start(), cannot enable InputFilter")
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to enable InputFilter in start(): ${t.message}")
                        }

                        val ims = inputManagerServiceInstance
                        if (ims != null) {
                            val existingFilter = try {
                                XposedHelpers.getObjectField(ims, "mInputFilter")
                            } catch (_: Throwable) { null }
                            if (existingFilter == null) {
                                registerFakeInputFilter(ims, classLoader)
                            }
                        }
                    }
                })

            return
        } catch (_: Throwable) {
        }

        try {
            val nativeMethod = inputManagerService.getDeclaredMethod(
                "nativeSetInputFilterEnabled",
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            nativeMethod.isAccessible = true

            XposedBridge.hookMethod(nativeMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[param.args.size - 1] = true
                }
            })

            XposedHelpers.findAndHookMethod(inputManagerService, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext")
                                as android.content.Context
                            GestureManager.initSystemServer(context)
                            AutomationTriggerEngine.initialize(context)
                            PremiumPluginLoader.verifyDeviceBinding(context)
                            notifyModuleLoaded(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Failed to initialize EdgeX runtime in start(): ${t.message}")
                        }

                        try {
                            val ptr = XposedHelpers.getLongField(param.thisObject, "mPtr")
                            nativeMethod.invoke(null, ptr, true)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG: Legacy InputFilter enable failed: ${t.message}")
                        }
                    }
                })

        } catch (t: Throwable) {
            XposedBridge.log("$TAG: All InputFilter enable approaches failed: ${t.message}")
        }
    }

    private fun registerFakeInputFilter(imsInstance: Any, classLoader: ClassLoader) {
        try {
            val iInputFilterClass = XposedHelpers.findClass("android.view.IInputFilter", classLoader)
            val iInputFilterHostClass = XposedHelpers.findClass("android.view.IInputFilterHost", classLoader)
            val sendInputEvent = iInputFilterHostClass.getMethod(
                "sendInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            var hostRef: Any? = null

            val filterProxy = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(iInputFilterClass),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    when (method.name) {
                        "install" -> {
                            hostRef = args?.get(0)
                        }
                        "filterInputEvent" -> {
                            val host = hostRef
                            val event = args?.get(0) as? android.view.InputEvent
                            val policyFlags = args?.get(1) as? Int ?: 0
                            if (host != null && event != null) {
                                try {
                                    sendInputEvent.invoke(host, event, policyFlags)
                                } catch (e: Exception) {
                                    XposedBridge.log("$TAG: sendInputEvent failed: ${e.message}")
                                }
                            }
                        }
                        "asBinder" -> android.os.Binder()
                        else -> null
                    }
                    null
                }
            )

            XposedHelpers.callMethod(imsInstance, "setInputFilter", filterProxy)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: registerFakeInputFilter failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
