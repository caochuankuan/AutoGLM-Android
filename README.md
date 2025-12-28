# 遇见手机助手 (AutoGLM Android)

基于 [AndroidAutoGLM](https://github.com/sidhu-master/AndroidAutoGLM) 二次开发的智能手机助手应用。

## 项目简介

遇见手机助手是一款基于 AI 大模型的智能手机操作助手，能够通过自然语言指令自动执行各种手机操作任务。本项目在原版 AndroidAutoGLM 基础上进行了重要优化和功能调整。

## 主要改进

### ✨ 新增功能
- **系统 TTS 语音播报**：集成系统文本转语音功能，实时播报任务状态和消息内容
- **聊天记录持久化**：保存用户与ai的对话
- **执行时长播报**：结束时语音播报耗时并震动
- **支持Shizuku**：支持使用Shizuku + [ADBKeyBoard](https://github.com/senzhk/ADBKeyBoard) 来实现输入文本成功率
- **获取安装应用列表**：可操作app扩展到全部app

### 🔧 功能调整  
- **移除语音输入模块**：简化交互方式，专注于文本输入体验
- **UI 界面优化**：改进用户界面设计，提升使用体验

### 🎨 界面优化
- 重新设计聊天界面布局
- 优化设置页面交互
- 改进悬浮窗显示效果

## 功能特性

- 🤖 **智能对话**：支持自然语言交互，理解复杂指令
- 📱 **自动操作**：通过无障碍服务自动执行手机操作
- 🔊 **语音播报**：实时语音反馈任务状态和结果
- ⚙️ **多模型支持**：兼容 OpenAI、智谱AI、DeepSeek、Google Gemini 等多种 API
- 🌐 **悬浮窗模式**：支持全局悬浮窗，随时唤起助手
- 💾 **对话记录**：自动保存聊天历史，支持清空管理

## 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构模式**：MVVM
- **网络请求**：Retrofit + OkHttp
- **数据存储**：DataStore + MMKV
- **系统服务**：AccessibilityService

## 系统要求

- Android 11.0 (API 30) 及以上
- 需要开启无障碍服务权限
- 需要授予悬浮窗权限
- 需要网络连接

## 安装使用

1.  获取 API Key：
    *   访问 [智谱AI开放平台 - API Key管理](https://bigmodel.cn/usercenter/proj-mgmt/apikeys)。
    *   登录您的账号（支持手机号或微信扫码）。
    *   在页面中点击“创建 API Key”或复制已有的 Key。
2. 打开应用，进入设置页面配置 API Key
3. 开启无障碍服务权限
4. 授予悬浮窗权限
5. 开始使用智能助手功能
6. (可选)如需使用Shuziku模式，请安装 [Shizuku](https://shizuku.rikka.app/download/) + [ADBKeyBoard](https://github.com/senzhk/ADBKeyBoard)

## 配置说明

### API 配置
- **API Key**：填入对应服务商的 API 密钥
- **Base URL**：配置 API 服务地址
- **模型名称**：选择使用的 AI 模型
- **API 类型**：支持 OpenAI 兼容接口和 Google Gemini

### TTS 设置
- **状态语音播报**：开启/关闭语音播报功能
- 使用系统内置 TTS 引擎，支持中英文播报

## 权限说明

- **网络权限**：用于 API 请求
- **无障碍服务**：执行自动化操作
- **悬浮窗权限**：显示全局悬浮窗
- **前台服务**：保持服务稳定运行

## 开发信息

- **包名**：com.yifeng.autogml
- **最低 SDK**：24 (Android 7.0)
- **目标 SDK**：36 (Android 14)

## 原项目致谢

本项目基于 [AndroidAutoGLM](https://github.com/sidhu-master/AndroidAutoGLM) 开发，感谢原作者的开源贡献。

## 许可证

本项目遵循原项目的开源许可证。

## 更新日志

### v1.0.0
- 基于 AndroidAutoGLM 进行二次开发
- 移除语音输入功能模块
- 新增系统 TTS 语音播报功能
- 优化 UI 界面设计
- 增强实时状态反馈
- 改进用户交互体验

### v1.0.2
- 结束时语音播报耗时

### v1.1.0
- 支持使用Shizuku + ADBKeyBoard来实现输入文本成功率

### v1.2.0
- 优化悬浮窗 UI
- 悬浮窗支持显示详细信息（点击文本切换）

### v1.3.0
- 获取安装应用列表，实现可操作app扩展到全部app
- 优化 UI