# Telegram Search Native

> 一个基于 **Kotlin + Jetpack Compose + TDLib** 的 Android 原生 Telegram 本地检索原型。用户在自己的手机完成 Telegram 登录，并自主选择会话同步；消息索引、API 配置和 TDLib 工作目录均保存在设备本地。
>
> **克隆源码后可使用自己的 Telegram API 参数构建 APK。** 可通过受 Git 忽略的 `local.properties`、环境变量或 Gradle 属性注入 `TG_DEFAULT_API_ID` 与 `TG_DEFAULT_API_HASH`；本项目不提供、不会公开任何其他人的 API 凭据。

## 项目来源

本项目参考 [telegram-search](https://github.com/groupultra/telegram-search) 的检索思路开发。原项目提供的是网页/服务端检索方案，并没有面向 Android 的原生应用；本项目据此实现了 Kotlin + Jetpack Compose + TDLib 的原生 Android 版本，将登录、会话同步、消息索引和离线搜索放在用户自己的设备上完成。

## 主要能力

| 能力 | 当前实现 |
|---|---|
| 本地 Telegram 登录 | 通过 TDLib 完成手机号、验证码、两步验证和其他设备确认状态处理。 |
| 选择性全量同步 | 按会话选择、名称搜索、已选置顶；会话列表支持右侧滑块快速定位，逐页请求可访问的历史消息，可手动停止。 |
| 后台同步 | 同步开始后可交给 Android 前台服务继续运行，切换到其他应用不影响索引；同步完成或停止后发送系统通知。 |
| 离线本地检索 | 支持单词和多关键词包含检索；结果按时间顺序显示，支持右侧滑块、多选、全选、批量本机收藏和批量删除索引。 |
| 消息详情 | 长文本滚动、长按选择文字、一键复制全文、单条本地删除。 |
| 本机收藏夹 | 搜索结果和消息详情可书签收藏；“收藏”页集中查看和移除收藏，支持与搜索页一致的多关键词检索及右侧滑块快速定位，不写入 Telegram 账号。 |
| 完整数据备份 | 设置页可将全部消息索引、本机收藏和设备内自定义 API 导出为 ZIP，也可在确认覆盖后导入恢复；Telegram 登录会话和 Android Keystore 不会导出。 |
| 本地索引管理 | 设置页支持删除全部搜索索引或按会话删除索引；不影响 Telegram 中的原始消息。 |
| Telegram 操作 | 尝试转发到 Saved Messages，并在 Telegram 提供外链时跳转官方客户端。 |
| 本地隐私 | 不含服务器、云同步或分析 SDK；手动填写的 API 配置通过 Android Keystore 支撑的加密偏好保存。 |
| 源码自定义构建 | 可通过本地 `local.properties`、环境变量或 Gradle 属性注入自己的 API 参数；正式 Release 与自行构建路径相互独立。 |
| API 优先级与反馈 | 设备内手动保存的 API 优先；未填写时才使用构建默认 API。Telegram 拒绝当前 API 时会明确说明来源与处理方式，不会静默回退。 |

## 快速开始

### 1. 取得 Telegram 开发者参数

在 [my.telegram.org/apps](https://my.telegram.org/apps) 使用**自己的账号**创建一个应用，得到 `api_id` 和 `api_hash`。这两项只应在本机直接粘贴到应用的“设置”页，**绝不能提交到 GitHub、聊天记录、Issue 或截图中**。

### 2. 安装预构建版本

在仓库的 [Releases](https://github.com/ZYJ882/telegram-search-native/releases) 页面下载 APK。首次安装调试签名 APK 时，Android 可能要求允许该来源安装应用。请直接覆盖安装更新版，避免不必要地清除本地索引和会话。

### 3. 连接与同步

在应用中依次打开“设置 → 连接 → 同步 → 搜索”。连接阶段的验证码和两步验证密码仅应在手机内输入。同步页支持按会话名称搜索，勾选后会话会自动置顶；只需点击一次同步。同步持续到 Telegram 返回空页或你主动点击停止。

点击“开始同步”后，任务会直接由 Android 前台服务负责执行，然后可以安全切换到其他应用。Android 会在通知栏持续显示同步状态；同步完成后会保留结果通知。首次使用 Android 13 及以上系统时，需要允许应用发送通知。

### 完整数据备份

在“设置 → 完整数据备份”中可以导出 ZIP 备份，内容包括全部本机消息索引、收藏状态和设备内自定义 API 配置。导入会覆盖当前本地索引、收藏和自定义 API，应用会在操作前再次确认。Telegram 登录会话、Android Keystore 和 TDLib 工作目录不会写入备份；导入后如需连接 Telegram，按当前设备的授权状态继续操作即可。

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

### 使用自己的 API 构建

公开源码不包含任何真实 API 参数。克隆后可继续在应用“设置”页手动填写，也可在构建时通过 `TG_DEFAULT_API_ID` 和 `TG_DEFAULT_API_HASH` 注入自己的默认参数。环境变量、Gradle 属性与 `local.properties` 的优先级和完整示例见 [使用自己的 Telegram API 构建](docs/BUILD_WITH_OWN_API.md)。手动保存到设备的 API 参数始终优先于构建默认值。

## 版本与下载

请前往 [GitHub Releases](https://github.com/ZYJ882/telegram-search-native/releases) 查看各版本。每个正式版本的 Release 页面均单独记录该版本的详细更新内容，并提供对应的 APK 与 SHA-256 校验文件；README 不重复列出逐版本更新说明。仓库不再通过 GitHub Actions 自动创建公开 Release，正式版本由维护者人工确认后发布。

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
