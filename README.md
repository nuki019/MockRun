# MockRun

Android 模拟定位跑步工具，通过 TestProvider 注入假位置，可模拟定点传送或沿指定路线匀速跑步。

## 功能

- **定点传送** — 将 GPS 位置瞬间跳转到第一个输入点
- **模拟跑步** — 沿 4 个点位循环移动，速度通过滑块调节（最大 8 m/s）
- **获取真实定位** — 每个坐标框旁均有按钮，可一键填入当前设备真实坐标
- **多点路线** — 支持 2~4 个坐标点，自动循环

## 使用前提

1. **开启开发者选项**  
   `设置 → 关于手机 → 连续点击"版本号"7 次`

2. **选择模拟位置应用**  
   `设置 → 系统 → 开发者选项 → 选择模拟位置信息应用` → 选择 **MockRun**

3. **授予定位权限**  
   首次运行时会弹出权限请求，选择"始终允许"

## 使用步骤

1. 在 4 个坐标框中输入 `纬度, 经度`（例如 `45.737196, 126.627842`），或点击"获取当前定位"自动填充
2. 调节底部滑块设置速度
3. 点击 **传送至点1** 实现瞬间跳转，或点击 **开始跑步** 模拟沿路线移动
4. 再次点击对应按钮即可停止

## 构建

```bash
# 命令行构建 debug APK
./gradlew assembleDebug

# 生成的 APK 路径
# app/build/outputs/apk/debug/app-debug.apk
```

- minSdk: 31（Android 12）
- targetSdk: 36

## 项目结构

```
app/src/main/java/com/example/mockrun/
├── MainActivity.kt          # UI 绑定、事件分发、权限处理
├── LocationParser.kt        # 坐标字符串解析
├── MockLocationProvider.kt  # TestProvider 创建 / 销毁 / 发送位置
└── RunningSimulator.kt      # 沿点位循环移动的步进计算
```

## 免责声明

本工具仅供学习 Android 定位服务机制使用。请勿用于欺骗健身/打卡类应用，使用后果由使用者自行承担。
