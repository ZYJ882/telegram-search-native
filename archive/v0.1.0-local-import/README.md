# Telegram Search Local for Android

这是一个独立实现的 **Kotlin + Jetpack Compose 原生 Android MVP**。它对应 `groupultra/telegram-search` 的移动端“本地导入与检索”核心场景，而不是该项目完整 Web／服务端能力的直接移植。

## 能力与隐私

应用通过 Android 系统文件选择器导入用户主动选择的 Telegram JSON／JSONL 导出文本，并把可检索字段写入应用私有 SQLite 数据库。它支持关键词匹配、关键词高亮、会话和日期条件过滤、消息上下文、收藏及一键清除索引。清单文件**没有**申请 `INTERNET`、通讯录或存储权限；本项目不包含 Telegram 登录、Telegram API 调用、云同步、后台上传或 AI 分析。

| 输入格式 | 支持情况 | 说明 |
|---|---:|---|
| Telegram Desktop `result.json` | 支持 | 读取 `messages` 内的文本消息 |
| 常见单聊天 JSON | 支持 | 支持 JSON 数组与 `messages`／`data` 容器 |
| tg-search CLI JSONL | 支持 | 每行视为一个 JSON 消息记录 |
| 图片、音频、视频二进制 | 不导入 | 保留文字检索的最小权限边界 |

## 在 Android Studio 运行

使用 Android Studio Ladybug 或更新版本打开本目录。首次同步会自动下载 Gradle 与 Maven 依赖。选择 Android SDK Platform 35，确认系统使用 JDK 21，然后运行 `app` 配置到 API 26+ 的设备或模拟器。

```bash
./gradlew :app:assembleDebug
```

构建成功的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。为避免在公共仓库中携带任何聊天文本，归档未包含示例导出；第一次打开后，请通过系统文件选择器导入你自己创建的匿名测试 JSON / JSONL 文件。

## 已知限制与下一步

此 MVP 采用 SQLite `LIKE` 做离线包含检索，以保持工程最小、易审计并直接支持中文字符匹配。对于非常大的归档，应升级到 FTS/n-gram 索引并使用流式 JSON 解析；向量搜索、Takeout 同步和 RAG 应作为可选功能，且必须经用户明确授权后才能引入。

## 参考

源项目的 README 说明了导出、分词／向量检索、RAG、Bot 与本地优先 CLI 能力；其 CLI 还明确规定，批量 Telegram Takeout 同步要由用户显式授权，并在本地执行查询与导出。Android MVP 仅借鉴这一本地优先的隐私原则，未复制其源代码。

- [groupultra/telegram-search README](https://github.com/groupultra/telegram-search)
- [@tg-search/cli README](https://github.com/groupultra/telegram-search/blob/main/packages/cli/README.md)
