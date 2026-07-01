package com.example.xposedswipe.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.xposedswipe.R;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_SWIPE = "com.example.xposedswipe.ACTION_SWIPE";

    private EditText etStartX, etStartY, etEndX, etEndY, etDuration, etAutoInterval;
    private Button btnToggleBall;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingBall();
                } else {
                    Toast.makeText(this, "未授予悬浮窗权限，无法显示悬浮球", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                requestOverlayPermissionThenStart();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etStartX = findViewById(R.id.etStartX);
        etStartY = findViewById(R.id.etStartY);
        etEndX = findViewById(R.id.etEndX);
        etEndY = findViewById(R.id.etEndY);
        etDuration = findViewById(R.id.etDuration);
        etAutoInterval = findViewById(R.id.etAutoInterval);
        btnToggleBall = findViewById(R.id.btnToggleBall);

        // 加载保存的设置
        loadSettings();

        // 快捷按钮：模拟标准上划
        findViewById(R.id.btnQuickSwipeUp).setOnClickListener(v ->
                sendSwipe(540f, 1900f, 540f, 400f, 300L));

        // 自定义参数发送
        findViewById(R.id.btnCustomSwipe).setOnClickListener(v -> {
            try {
                float startX = Float.parseFloat(etStartX.getText().toString());
                float startY = Float.parseFloat(etStartY.getText().toString());
                float endX = Float.parseFloat(etEndX.getText().toString());
                float endY = Float.parseFloat(etEndY.getText().toString());
                long duration = Long.parseLong(etDuration.getText().toString());
                sendSwipe(startX, startY, endX, endY, duration);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入合法的数字", Toast.LENGTH_SHORT).show();
            }
        });

        // 保存设置按钮
        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveSettings());

        // 悬浮球开关
        btnToggleBall.setOnClickListener(v -> {
            if (FloatingBallService.isRunning) {
                stopFloatingBall();
            } else {
                requestNotificationPermissionThenStart();
            }
        });

        updateToggleButtonText();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(FloatingBallService.PREFS_NAME, MODE_PRIVATE);
        etStartX.setText(String.valueOf(prefs.getFloat("startX", 540f)));
        etStartY.setText(String.valueOf(prefs.getFloat("startY", 1900f)));
        etEndX.setText(String.valueOf(prefs.getFloat("endX", 540f)));
        etEndY.setText(String.valueOf(prefs.getFloat("endY", 400f)));
        etDuration.setText(String.valueOf(prefs.getLong("duration", 300L)));
        etAutoInterval.setText(String.valueOf(prefs.getLong("auto_swipe_interval", 5000L)));
    }

    private void saveSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences(FloatingBallService.PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putFloat("startX", Float.parseFloat(etStartX.getText().toString()))
                    .putFloat("startY", Float.parseFloat(etStartY.getText().toString()))
                    .putFloat("endX", Float.parseFloat(etEndX.getText().toString()))
                    .putFloat("endY", Float.parseFloat(etEndY.getText().toString()))
                    .putLong("duration", Long.parseLong(etDuration.getText().toString()))
                    .putLong("auto_swipe_interval", Long.parseLong(etAutoInterval.getText().toString()))
                    .apply();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入合法的数字", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleButtonText();
    }

    private void updateToggleButtonText() {
        btnToggleBall.setText(FloatingBallService.isRunning ? "关闭悬浮球" : "开启悬浮球");
    }

    private void requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            requestOverlayPermissionThenStart();
        }
    }

    private void requestOverlayPermissionThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            startFloatingBall();
        }
    }

    private void startFloatingBall() {
        Intent serviceIntent = new Intent(this, FloatingBallService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        updateToggleButtonText();
        Toast.makeText(this, "悬浮球已开启，点击切换自动上划", Toast.LENGTH_SHORT).show();
    }

    private void stopFloatingBall() {
        Intent serviceIntent = new Intent(this, FloatingBallService.class);
        serviceIntent.setAction(FloatingBallService.ACTION_STOP);
        startService(serviceIntent);
        updateToggleButtonText();
    }

    private void sendSwipe(float startX, float startY, float endX, float endY, long duration) {
        SharedPreferences prefs = getSharedPreferences(FloatingBallService.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putFloat("startX", startX)
                .putFloat("startY", startY)
                .putFloat("endX", endX)
                .putFloat("endY", endY)
                .putLong("duration", duration)
                .apply();

        Intent intent = new Intent(ACTION_SWIPE);
        intent.putExtra("startX", startX);
        intent.putExtra("startY", startY);
        intent.putExtra("endX", endX);
        intent.putExtra("endY", endY);
        intent.putExtra("duration", duration);
        sendBroadcast(intent);
        Toast.makeText(this, "已发送滑动指令", Toast.LENGTH_SHORT).show();
    }
}