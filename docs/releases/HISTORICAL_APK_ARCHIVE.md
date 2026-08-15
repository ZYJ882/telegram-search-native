# 历史 APK 归档

本归档包含 Telegram Search Native 的历史调试签名 APK 及对应 SHA-256 校验文件。文件名包含版本号与功能代号，适用于版本回溯和覆盖安装测试。

| APK 版本 | 主要更新 |
|---|---|
| v0.2.0-tdlib | TDLib 登录、会话同步与本地搜索。 |
| v0.2.1-code-retry | 验证码错误恢复。 |
| v0.3.0-full-history | 分页历史同步。 |
| v0.3.2-sync-icon | 同步稳定性、媒体配文索引与启动图标。 |
| v0.4.0-chat-picker | 会话搜索与已选会话置顶。 |
| v0.5.0-message-actions | Saved Messages 转发、Telegram 跳转与导航体验。 |
| v0.5.1-message-refresh-copy | 消息权限刷新与文本复制。 |
| v0.6.0-scroll-pagination-fix | 长消息滚动与历史分页游标优化。 |

## 文件校验

每个 APK 均配有同名 `SHA256.txt` 文件。下载后可使用以下命令校验文件完整性：

```bash
sha256sum TelegramSearchNative-<version>-debug.apk
```

## 源码范围

根目录 `app/` 为当前 v0.7.0 的可构建源码；`archive/` 包含 v0.1.0 和 v0.2.0 的保留源码快照。v0.3.1 为图标设计迭代，其改动已纳入 v0.3.2；其余历史版本以 APK、校验文件、`CHANGELOG.md` 和 `docs/releases/` 中的版本说明提供归档信息。
