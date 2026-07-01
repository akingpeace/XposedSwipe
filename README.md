# XposedSwipe

一个基于 Xposed/LSPosed 的模块，用于在系统层面自由模拟滑动（上划/任意方向）手势。

## 原理

1. 模块 Hook `android` 包（即 system_server 进程），运行在系统权限上下文中。
2. system_server 启动完成后，注册一个 `BroadcastReceiver`，监听自定义广播 `com.example.xposedswipe.ACTION_SWIPE`。
3. 外部任意 App（本项目自带一个简单控制面板 `MainActivity`）发送该广播，携带起点坐标、终点坐标、耗时。
4. `GestureInjector` 用反射调用 `InputManager.injectInputEvent()`，构造 DOWN -> 多个 MOVE -> UP 的 `MotionEvent` 序列，注入到系统输入事件流中，效果等同于真实手指滑动。

因为是在 system_server 里直接调用，天然拥有系统权限，不需要额外申请 `INJECT_EVENTS` 权限，也不依赖 `adb shell input swipe`。

## 使用步骤

1. 用 Android Studio 打开本项目，编译出 APK。
2. 手机需要已 root，并安装 LSPosed（推荐）或 EdXposed 框架。
3. 安装该 APK 后，在 LSPosed 管理器中激活「XposedSwipe」模块，作用域勾选 **系统框架 (android)**。
4. 重启设备使 Hook 生效。
5. 打开「XposedSwipe 控制面板」App：
   - 点击「快捷：模拟一次标准上划」，即可看到屏幕执行一次从下往上的滑动。
   - 也可以自定义起点/终点坐标和耗时，点「发送自定义滑动」。
   - **悬浮球**：点击「开启悬浮球」，首次使用会依次申请「通知权限」（Android 13+）和「悬浮窗权限」，都同意后屏幕上会出现一个可拖动的蓝色圆形按钮。之后无论在任何 App 界面，单击悬浮球即可触发一次滑动（参数使用你在控制面板里最近一次发送过的坐标/时长，默认是标准上划）。拖动悬浮球可以改变它的位置。点击通知栏里的常驻通知可以关闭悬浮球。
6. 可用 `adb logcat -s LSPosedBridge` 或 LSPosed 日志查看 `XposedSwipe:` 开头的调试信息，确认 Hook 点是否生效。

## 关于兼容性（重要）

不同厂商 ROM（MIUI/ColorOS/EMUI 等）和不同 Android 版本，`system_server` 内部启动流程的类名、方法名可能有差异。本项目里 `MainHook.java` 已经做了两级尝试：

- 首选：Hook `SystemServiceManager.startBootPhase`，在 `PHASE_BOOT_COMPLETED` (1000) 时注册接收器（Android 8+ 通用性较好）。
- 备用：Hook `ActivityManagerService.systemReady`。

如果两个都失败，日志会打印失败原因，此时需要你根据自己设备的 `frameworks/base` 源码，找到系统启动完成的合适 Hook 点，替换 `MainHook.java` 里对应部分。

Android 13+ 由于隐式广播限制，注册时使用了 `RECEIVER_EXPORTED` 标志，如果你的目标 API 更低，请自行调整 `MainHook.registerReceiver()` 里的判断逻辑。

## 可以扩展的方向

- 把「发送广播」换成本地 Socket 或 ContentProvider，减少广播被系统限流/拦截的可能性。
- 增加多点触控、贝塞尔曲线轨迹模拟真实手指滑动的加速度曲线，规避某些 App 的反自动化检测。
- 悬浮球长按弹出多个方向的快捷按钮（上下左右滑动），或加入拖动到屏幕边缘自动贴边隐藏的效果。
- 增加坐标可视化选择器（在悬浮层上点两下确定起止点，自动填入控制面板）。

## 免责声明

本项目仅用于学习 Android Hook / Input 注入机制、自动化测试、无障碍辅助等正当用途。
请遵守当地法律法规及目标 App 的服务条款，不要用于任何违反平台规则、侵犯他人权益的用途。
