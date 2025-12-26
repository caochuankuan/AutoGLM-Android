# Shizuku模式设置指南

## 什么是Shizuku模式？

Shizuku模式允许应用通过Shizuku服务执行系统级操作，无需无障碍服务即可实现点击、滑动、输入等功能。

## 设置步骤

### 1. 安装Shizuku

从以下渠道下载并安装Shizuku：
- [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases)
- Google Play Store
- F-Droid

### 2. 启动Shizuku服务

有两种方式启动Shizuku服务：

#### 方式一：通过ADB（推荐）
1. 在电脑上安装ADB工具
2. 手机开启开发者选项和USB调试
3. 连接手机到电脑
4. 运行命令：
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```

#### 方式二：通过Root（需要Root权限）
1. 手机已获得Root权限
2. 在Shizuku应用中选择"通过Root启动"

### 3. 授权应用

1. 启动Shizuku服务后，打开遇见手机助手
2. 进入设置页面
3. 开启"Shizuku模式"开关
4. 首次开启时会弹出权限请求，点击"允许"

### 4. 验证设置

在设置页面中，如果Shizuku模式开关下方显示"Shizuku已就绪"，说明设置成功。

## 优势

- **无需无障碍服务**：不依赖无障碍服务，避免相关限制
- **更高权限**：可以执行更多系统级操作
- **更稳定**：相比无障碍服务更加稳定可靠

## 注意事项

- Shizuku服务需要在每次重启手机后重新启动
- 某些操作可能需要特定的系统权限
- 建议优先使用ADB方式启动，更加稳定

## 故障排除

### Shizuku服务未运行
- 检查Shizuku应用是否已安装
- 重新执行启动命令
- 确认ADB连接正常

### 权限未授予
- 在Shizuku应用的"已授权的应用"中检查是否包含遇见手机助手
- 重新开启Shizuku模式开关触发权限请求

### 操作执行失败
- 检查目标应用是否支持相应操作
- 尝试关闭Shizuku模式，使用无障碍服务模式