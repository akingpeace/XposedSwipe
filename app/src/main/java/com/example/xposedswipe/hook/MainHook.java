package com.example.xposedswipe.hook;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 只对 "android" 包（即 system_server 进程）生效。
 * 目标：在系统启动完成后，拿到系统级 Context，注册一个广播接收器，
 * 等待外部指令来触发滑动手势注入。
 *
 * 注意：不同厂商 ROM / 不同 Android 版本，system_server 内部类和
 * 启动流程方法名可能有差异，这里做了几种常见 Hook 点的尝试，
 * 具体以 Xposed / LSPosed 日志输出为准，必要时需要你根据自己
 * 设备的系统版本调整 Hook 点。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static boolean receiverRegistered = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("XposedSwipe: 已进入 system_server 进程, Android SDK=" + Build.VERSION.SDK_INT);

        // 方案一（推荐，Android 8+ 通用）：Hook SystemServiceManager.startBootPhase，
        // 在 PHASE_BOOT_COMPLETED (1000) 时注册广播接收器。
        try {
            Class<?> ssmClass = XposedHelpers.findClass(
                    "com.android.server.SystemServiceManager", lpparam.classLoader);

            XposedBridge.hookAllMethods(ssmClass, "startBootPhase", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int phase = -1;
                        for (Object arg : param.args) {
                            if (arg instanceof Integer) {
                                phase = (Integer) arg;
                            }
                        }
                        // SystemService.PHASE_BOOT_COMPLETED = 1000
                        if (phase == 1000) {
                            registerReceiver(lpparam);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("XposedSwipe: startBootPhase 处理异常: " + t);
                    }
                }
            });
            XposedBridge.log("XposedSwipe: 已挂钩 SystemServiceManager.startBootPhase");
        } catch (Throwable t) {
            XposedBridge.log("XposedSwipe: 挂钩 SystemServiceManager 失败，尝试备用方案: " + t);

            // 方案二（备用）：Hook ActivityManagerService.systemReady
            try {
                Class<?> amsClass = XposedHelpers.findClass(
                        "com.android.server.am.ActivityManagerService", lpparam.classLoader);

                XposedBridge.hookAllMethods(amsClass, "systemReady", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        registerReceiver(lpparam);
                    }
                });
                XposedBridge.log("XposedSwipe: 已挂钩 ActivityManagerService.systemReady (备用方案)");
            } catch (Throwable t2) {
                XposedBridge.log("XposedSwipe: 备用方案也失败了，请根据你的系统版本自行调整 Hook 点: " + t2);
            }
        }
    }

    private synchronized void registerReceiver(XC_LoadPackage.LoadPackageParam lpparam) {
        if (receiverRegistered) {
            return;
        }
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass(
                    "android.app.ActivityThread", lpparam.classLoader);

            Object activityThread = XposedHelpers.callStaticMethod(
                    activityThreadClass, "currentActivityThread");

            Context systemContext = (Context) XposedHelpers.callMethod(
                    activityThread, "getSystemContext");

            if (systemContext == null) {
                XposedBridge.log("XposedSwipe: 获取 systemContext 失败");
                return;
            }

            IntentFilter filter = new IntentFilter(SwipeReceiver.ACTION_SWIPE);

            if (Build.VERSION.SDK_INT >= 33) {
                // Android 13+ 要求显式声明 exported 标志
                Method registerReceiver = Context.class.getMethod(
                        "registerReceiver",
                        android.content.BroadcastReceiver.class,
                        IntentFilter.class,
                        int.class);
                // Context.RECEIVER_EXPORTED = 0x2
                registerReceiver.invoke(systemContext, new SwipeReceiver(), filter, 0x2);
            } else {
                systemContext.registerReceiver(new SwipeReceiver(), filter);
            }

            receiverRegistered = true;
            XposedBridge.log("XposedSwipe: 广播接收器注册成功，等待滑动指令");
        } catch (Throwable t) {
            XposedBridge.log("XposedSwipe: 注册广播接收器失败: " + t);
        }
    }
}
