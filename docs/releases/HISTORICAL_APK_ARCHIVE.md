# 历史 APK 归档

本发布集中保存已交付的调试签名 APK 与对应 SHA-256 校验文件，供回溯功能行为和进行覆盖安装测试。每个文件名都包含其版本号和功能代号。

| APK 版本 | 主要内容 |
|---|---|
| v0.2.0-tdlib | 首个 TDLib 登录、会话同步与本地搜索版本。 |
| v0.2.1-code-retry | 验证码错误恢复。 |
| v0.3.0-full-history | 分页历史同步。 |
| v0.3.2-sync-icon | 同步修复、媒体配文索引和图标。 |
| v0.4.0-chat-picker | 会话搜索和已选置顶。 |
| v0.5.0-message-actions | 收藏夹转发、Telegram 跳转和导航修复。 |
| v0.5.1-message-refresh-copy | 消息权限刷新、复制全文。 |
| v0.6.0-scroll-pagination-fix | 长消息滚动和历史分页游标修复。 |

每个 APK 都配有同名 `SHA256.txt` 文件。下载后可使用如下命令校验文件完整性：

```bash
sha256sum TelegramSearchNative-<version>-debug.apk
```

历史中间里程碑 v0.3.1 仅为图标设计迭代，未保留独立 APK；其变更已合并到 v0.3.2。根目录的 `app/` 是当前 v0.7.0 的可构建源码；`archive/` 仅包含实际保留的 v0.1.0 和 v0.2.0 源码快照。其余历史版本以本 Release 的 APK、校验文件和 `CHANGELOG.md` / `docs/releases/` 中的说明作为归档，避免将当前源码误标为旧版本。
