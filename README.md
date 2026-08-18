# Telegram Search Native

> 一个基于 **Kotlin + Jetpack Compose + TDLib** 的 Android 原生 Telegram 本地检索原型。用户在自己的手机完成 Telegram 登录，并自主选择会话同步；消息索引、API 配置和 TDLib 工作目录均保存在设备本地。

## 主要能力

| 能力 | 当前实现 |
|---|---|
| 本地 Telegram 登录 | 通过 TDLib 完成手机号、验证码、两步验证和其他设备确认状态处理。 |
| 选择性全量同步 | 按会话选择、名称搜索、已选置顶；会话列表支持右侧滑块快速定位，逐页请求可访问的历史消息，可手动停止。 |
| 离线本地检索 | 支持单词和多关键词包含检索；结果按时间顺序显示，支持右侧滑块、多选、全选、批量本机收藏和批量删除索引。 |
| 消息详情 | 长文本滚动、长按选择文字、一键复制全文、单条本地删除。 |
| 本机收藏夹 | 搜索结果和消息详情可书签收藏；“收藏”页集中查看和移除收藏，支持右侧滑块快速定位，不写入 Telegram 账号。 |
| 本地索引管理 | 设置页支持删除全部搜索索引或按会话删除索引；不影响 Telegram 中的原始消息。 |
| Telegram 操作 | 尝试转发到 Saved Messages，并在 Telegram 提供外链时跳转官方客户端。 |
| 本地隐私 | 不含服务器、云同步或分析 SDK；API 配置通过 Android Keystore 支撑的加密偏好保存。 |

## 快速开始

### 1. 取得 Telegram 开发者参数

在 [my.telegram.org/apps](https://my.telegram.org/apps) 使用**自己的账号**创建一个应用，得到 `api_id` 和 `api_hash`。这两项只应在本机直接粘贴到应用的“设置”页，**绝不能提交到 GitHub、聊天记录、Issue 或截图中**。

### 2. 安装预构建版本

在仓库的 [Releases](https://github.com/ZYJ882/telegram-search-native/releases) 页面下载 APK。首次安装调试签名 APK 时，Android 可能要求允许该来源安装应用。请直接覆盖安装更新版，避免不必要地清除本地索引和会话。

### 3. 连接与同步

在应用中依次打开“设置 → 连接 → 同步 → 搜索”。连接阶段的验证码和两步验证密码仅应在手机内输入。同步页支持按会话名称搜索，勾选后会话会自动置顶；只需点击一次同步。同步持续到 Telegram 返回空页或你主动点击停止。

## 从源码构建

项目使用 Android Gradle Plugin 与 Gradle Wrapper。要求 Android SDK Platform 35、Build Tools 35 和 JDK 17 或更高版本。

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 版本与下载

| 版本 | 核心更新 | 发布页 |
|---|---|---|
| v1.4.0 | 收藏页与同步页增加右侧垂直滑块，可快速定位长列表。 | [Release v1.4.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.4.0-multi-page-slider) |
| v1.3.1 | 移除 # 与字母分组，搜索结果按时间顺序显示。 | [Release v1.3.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.3.1-time-list) |
| v1.3.0 | 搜索结果长按多选、全选、批量本机收藏与批量删除。 | [Release v1.3.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.3.0-multi-select) |
| v1.2.0 | 搜索索引管理、全部索引删除与按会话删除。 | [Release v1.2.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.2.0-index-manager) |
| v1.1.0 | 右侧垂直滑块、宽触摸热区、进度浮层与快速定位。 | [Release v1.1.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.1.0-search-slider) |
| v1.0.1 | 覆盖层连续触摸追踪，修复右侧字母区滑动。 | [Release v1.0.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.0.1-pointer-overlay) |
| v1.0.0 | 紧凑抽屉式字母索引、长按滑动浮层和搜索列表滚动优化。 | [Release v1.0.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v1.0.0-drawer-index) |
| v0.9.1 | 修复右侧字母索引条上下拖动跳转。 | [Release v0.9.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.9.1-index-drag-fix) |
| v0.9.0 | 搜索结果右侧 #–Z 索引、会话名称分组和上下拖动快速定位。 | [Release v0.9.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.9.0-search-index-rail) |
| v0.8.1 | 多关键词全部包含检索，支持空格和常见分隔符。 | [Release v0.8.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.8.1-multi-keyword-search) |
| v0.8.0 | 本机收藏夹、单条本地删除与本地数据清除。 | [Release v0.8.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.8.0-local-library) |
| v0.7.0 | 持续历史同步、重复页不中止、页数诊断和手动停止。 | [Release v0.7.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.7.0) |
| v0.6.0 | 长消息详情滚动、稳定历史游标与重复页保护。 | [Release v0.6.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.6.0-scroll-pagination-fix) |
| v0.5.1 | 消息权限刷新、文本选择和全文复制。 | [Release v0.5.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.5.1-message-refresh-copy) |
| v0.5.0 | Saved Messages 转发、Telegram 跳转和导航体验。 | [Release v0.5.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.5.0-message-actions) |
| v0.4.0 | 会话名称搜索与已选会话置顶。 | [Release v0.4.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.4.0-chat-picker) |
| v0.3.2 | 同步稳定性、媒体配文索引和启动图标。 | [Release v0.3.2](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.3.2-sync-icon) |
| v0.3.1 | 启动图标设计迭代。 | [Release v0.3.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.3.1-history-icon) |
| v0.3.0 | 分页全量历史同步。 | [Release v0.3.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.3.0-full-history) |
| v0.2.1 | 验证码错误恢复和认证状态提示。 | [Release v0.2.1](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.2.1-code-retry) |
| v0.2.0 | TDLib 登录、会话同步与本地搜索。 | [Release v0.2.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.2.0-tdlib) |
| v0.1.0 | JSON/JSONL 本地导入与离线检索。 | [Release v0.1.0](https://github.com/ZYJ882/telegram-search-native/releases/tag/v0.1.0-local-import) |

更详细的变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 隐私与安全

**本仓库不包含、也不应包含**任何真实的 `api_id`、`api_hash`、Telegram 验证码、两步验证密码、TDLib 会话数据库、Android Keystore 文件或个人聊天数据。`.gitignore` 已主动排除这些路径；公开发布前仍应运行自己的密钥扫描。

Telegram 对消息历史、转发和消息外链拥有最终权限控制。应用会尽力通过 TDLib 请求可访问历史和当前消息权限，但不能绕过频道内容保护、私密会话限制或 Telegram 服务端策略。

## 许可与免责声明

这是个人实验性客户端原型，不隶属于 Telegram。使用者应遵守 Telegram 的 API 条款、当地法律及各频道的内容规则。请勿用它规避访问限制、内容保护或进行批量滥用。

## 目录结构

```text
app/                    Android App 源码
app/src/main/java/      Kotlin 与 Compose 实现
docs/releases/          每个版本的中文说明
gradle/                 Gradle Wrapper
CHANGELOG.md            版本历史
```

