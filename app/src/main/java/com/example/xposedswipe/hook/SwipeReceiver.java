package com.example.xposedswipe.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.robv.android.xposed.XposedBridge;

public class SwipeReceiver extends BroadcastReceiver {

    // 外部 App 发送广播时使用的 action，需与 MainActivity 中保持一致
    public static final String ACTION_SWIPE = "com.example.xposedswipe.ACTION_SWIPE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SWIPE.equals(intent.getAction())) {
            return;
        }

        float startX = intent.getFloatExtra("startX", 500f);
        float startY = intent.getFloatExtra("startY", 1800f);
        float endX = intent.getFloatExtra("endX", 500f);
        float endY = intent.getFloatExtra("endY", 400f);
        long duration = intent.getLongExtra("duration", 300L);

        XposedBridge.log(String.format(
                "XposedSwipe: 收到滑动指令 (%.0f,%.0f) -> (%.0f,%.0f) duration=%d",
                startX, startY, endX, endY, duration));

        GestureInjector.swipe(startX, startY, endX, endY, duration);
    }
}
