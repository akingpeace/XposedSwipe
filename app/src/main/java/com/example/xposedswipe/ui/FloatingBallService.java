package com.example.xposedswipe.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.xposedswipe.R;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FloatingBallService extends Service {

    public static final String ACTION_STOP = "com.example.xposedswipe.ACTION_STOP_FLOATING_BALL";
    public static final String PREFS_NAME = "xposedswipe_prefs";

    private static final String CHANNEL_ID = "xposedswipe_floating_ball";
    private static final int NOTIFICATION_ID = 1001;

    // 悬浮球固定尺寸（dp）
    private static final int BALL_SIZE_DP = 68;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;

    private float initialTouchX, initialTouchY;
    private int initialX, initialY;
    private long touchDownTime;
    private int clickDragToleranceIsPx;

    public static volatile boolean isRunning = false;
    private boolean isAutoSwipeEnabled = false;

    private ScheduledExecutorService scheduler;
    private Handler mainHandler;
    private Runnable autoSwipeTask;
    private long autoInterval = 5000L; // 默认5秒

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        addFloatingBall();
        isRunning = true;
        mainHandler = new Handler(Looper.getMainLooper());

        // 恢复自动上划状态
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isAutoSwipeEnabled = prefs.getBoolean("auto_swipe_enabled", false);
        autoInterval = prefs.getLong("auto_swipe_interval", 5000L);

        if (isAutoSwipeEnabled) {
            startAutoSwipe();
        }
        updateBallAppearance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        stopAutoSwipe();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void addFloatingBall() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int ballSizePx = dpToPx(BALL_SIZE_DP);
        clickDragToleranceIsPx = dpToPx(6);

        layoutParams = new WindowManager.LayoutParams(
                ballSizePx,
                ballSizePx,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 0;
        layoutParams.y = 400;

        windowManager.addView(floatingView, layoutParams);

        TextView ball = floatingView.findViewById(R.id.tvBall);
        ball.setOnTouchListener(this::onBallTouch);
    }

    private boolean onBallTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = layoutParams.x;
                initialY = layoutParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                touchDownTime = System.currentTimeMillis();
                return true;

            case MotionEvent.ACTION_MOVE:
                int dx = (int) (event.getRawX() - initialTouchX);
                int dy = (int) (event.getRawY() - initialTouchY);
                layoutParams.x = initialX + dx;
                layoutParams.y = initialY + dy;
                windowManager.updateViewLayout(floatingView, layoutParams);
                return true;

            case MotionEvent.ACTION_UP:
                int totalDx = (int) (event.getRawX() - initialTouchX);
                int totalDy = (int) (event.getRawY() - initialTouchY);
                long duration = System.currentTimeMillis() - touchDownTime;
                boolean isClick = Math.abs(totalDx) < clickDragToleranceIsPx
                        && Math.abs(totalDy) < clickDragToleranceIsPx
                        && duration < 300;
                if (isClick) {
                    // 点击切换自动上划开关
                    toggleAutoSwipe();
                }
                return true;
        }
        return false;
    }

    /**
     * 切换自动上划模式（点击悬浮球触发）
     */
    private void toggleAutoSwipe() {
        isAutoSwipeEnabled = !isAutoSwipeEnabled;

        // 保存状态
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean("auto_swipe_enabled", isAutoSwipeEnabled).apply();

        if (isAutoSwipeEnabled) {
            startAutoSwipe();
            showToast("✅ 自动上划已开启");
            // 点击反馈：绿色闪烁
            if (floatingView != null) {
                floatingView.animate()
                        .scaleX(1.3f).scaleY(1.3f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            if (floatingView != null) {
                                floatingView.animate()
                                        .scaleX(1f).scaleY(1f)
                                        .setDuration(150)
                                        .start();
                            }
                        })
                        .start();
            }
        } else {
            stopAutoSwipe();
            showToast("⏹ 自动上划已关闭");
            // 点击反馈：红色闪烁
            if (floatingView != null) {
                floatingView.animate()
                        .scaleX(0.7f).scaleY(0.7f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            if (floatingView != null) {
                                floatingView.animate()
                                        .scaleX(1f).scaleY(1f)
                                        .setDuration(150)
                                        .start();
                            }
                        })
                        .start();
            }
        }

        updateBallAppearance();
        updateNotification();
    }

    /**
     * 开始自动上划任务
     */
    private void startAutoSwipe() {
        stopAutoSwipe(); // 先停止旧的

        // 获取最新间隔
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        autoInterval = prefs.getLong("auto_swipe_interval", 5000L);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        autoSwipeTask = () -> {
            while (isAutoSwipeEnabled && isRunning) {
                try {
                    // 等待间隔时间
                    Thread.sleep(autoInterval);

                    // 在主线程执行滑动
                    mainHandler.post(() -> {
                        if (isAutoSwipeEnabled && isRunning) {
                            triggerSwipe();
                            // 执行时闪烁反馈
                            if (floatingView != null) {
                                floatingView.animate()
                                        .scaleX(1.2f).scaleY(1.2f)
                                        .setDuration(80)
                                        .withEndAction(() -> {
                                            if (floatingView != null) {
                                                floatingView.animate()
                                                        .scaleX(1f).scaleY(1f)
                                                        .setDuration(80)
                                                        .start();
                                            }
                                        })
                                        .start();
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        };
        scheduler.execute(autoSwipeTask);
    }

    /**
     * 停止自动上划
     */
    private void stopAutoSwipe() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        autoSwipeTask = null;
    }

    /**
     * 执行滑动
     */
    private void triggerSwipe() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float startX = prefs.getFloat("startX", 540f);
        float startY = prefs.getFloat("startY", 1900f);
        float endX = prefs.getFloat("endX", 540f);
        float endY = prefs.getFloat("endY", 400f);
        long duration = prefs.getLong("duration", 300L);

        Intent intent = new Intent(MainActivity.ACTION_SWIPE);
        intent.putExtra("startX", startX);
        intent.putExtra("startY", startY);
        intent.putExtra("endX", endX);
        intent.putExtra("endY", endY);
        intent.putExtra("duration", duration);
        sendBroadcast(intent);
    }

    /**
     * 显示Toast（在主线程）
     */
    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(FloatingBallService.this, msg, Toast.LENGTH_SHORT).show());
    }

    /**
     * 更新悬浮球外观
     */
    private void updateBallAppearance() {
        if (floatingView == null) return;
        TextView ball = floatingView.findViewById(R.id.tvBall);
        if (isAutoSwipeEnabled) {
            ball.setText("▶");  // 播放图标 - 表示自动运行中
            ball.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // 绿色
        } else {
            ball.setText("⏸");  // 暂停图标 - 表示已停止
            ball.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336)); // 红色
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "悬浮球服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("XposedSwipe 悬浮球常驻通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, FloatingBallService.class);
        stopIntent.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags);

        String statusText = isAutoSwipeEnabled ? "🟢 自动运行中 (点击悬浮球关闭)" : "🔴 已暂停 (点击悬浮球开启)";

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("XposedSwipe 悬浮球 " + (isAutoSwipeEnabled ? "🟢运行中" : "🔴已暂停"))
                .setContentText("点击悬浮球切换自动上划 | " + statusText)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭悬浮球", stopPendingIntent)
                .setOngoing(true)
                .build();
    }
}