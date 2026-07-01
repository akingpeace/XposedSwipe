package com.example.xposedswipe.hook;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * 负责构造 MotionEvent 并通过 InputManager.injectInputEvent 注入到系统。
 * 因为本类是在 system_server 进程（Hook "android" 包）里被调用的，
 * 所以拥有系统权限，可以直接注入，不需要 INJECT_EVENTS 权限申请。
 */
public class GestureInjector {

    private static final String TAG = "XposedSwipe";

    // INJECT_INPUT_EVENT_MODE_ASYNC = 0，异步注入，不等待事件被消费
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private static Object inputManagerInstance;
    private static Method injectInputEventMethod;

    static {
        try {
            Class<?> imClass = Class.forName("android.hardware.input.InputManager");
            Method getInstance = imClass.getMethod("getInstance");
            inputManagerInstance = getInstance.invoke(null);
            injectInputEventMethod = imClass.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
        } catch (Throwable t) {
            XposedBridge.log("XposedSwipe: 初始化 InputManager 反射失败: " + t);
        }
    }

    /**
     * 模拟一次滑动手势。
     *
     * @param startX   起点 X 坐标（像素）
     * @param startY   起点 Y 坐标（像素）
     * @param endX     终点 X 坐标（像素）
     * @param endY     终点 Y 坐标（像素）
     * @param duration 滑动耗时，单位毫秒
     */
    public static void swipe(final float startX, final float startY,
                              final float endX, final float endY,
                              final long duration) {
        if (injectInputEventMethod == null) {
            XposedBridge.log("XposedSwipe: injectInputEventMethod 未初始化，无法注入");
            return;
        }

        new Thread(() -> {
            try {
                long downTime = SystemClock.uptimeMillis();
                int steps = (int) Math.max(1, duration / 10); // 每 10ms 一帧
                long stepDelay = Math.max(1, duration / steps);

                injectMotionEvent(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY);

                for (int i = 1; i <= steps; i++) {
                    float progress = (float) i / steps;
                    float x = startX + (endX - startX) * progress;
                    float y = startY + (endY - startY) * progress;
                    long eventTime = SystemClock.uptimeMillis();
                    injectMotionEvent(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y);
                    Thread.sleep(stepDelay);
                }

                long upTime = SystemClock.uptimeMillis();
                injectMotionEvent(downTime, upTime, MotionEvent.ACTION_UP, endX, endY);

            } catch (Throwable t) {
                XposedBridge.log("XposedSwipe: 注入滑动失败: " + t);
            }
        }).start();
    }

    private static void injectMotionEvent(long downTime, long eventTime, int action,
                                           float x, float y) throws Exception {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injectInputEventMethod.invoke(inputManagerInstance, event, INJECT_INPUT_EVENT_MODE_ASYNC);
        event.recycle();
    }
}
