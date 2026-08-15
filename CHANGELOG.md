# 更新日志

本文件记录 Telegram Search Native 的版本演进。所有 APK 为调试签名构建，并在 GitHub Releases 中提供对应的 SHA-256 校验文件。

> 仓库根目录保留当前可构建的 v0.7.0 源码；`archive/` 包含 v0.1.0 与 v0.2.0 的源码快照。未单独保留源码快照的历史版本以 APK、校验文件和版本说明进行归档。

| 版本 | APK | 主要更新 |
|---|---|---|
| v0.1.0-local-import | 有 | JSON/JSONL 本地导入与离线检索。 |
| v0.2.0-tdlib | 有 | TDLib 登录、会话同步与本地搜索。 |
| v0.2.1-code-retry | 有 | 验证码错误恢复与认证状态提示。 |
| v0.3.0-full-history | 有 | 分页历史同步。 |
| v0.3.1-history-icon | 无单独 APK | 启动图标设计迭代。 |
| v0.3.2-sync-icon | 有 | 同步稳定性、媒体配文索引与图标更新。 |
| v0.4.0-chat-picker | 有 | 会话名称搜索与已选会话置顶。 |
| v0.5.0-message-actions | 有 | Saved Messages 转发、Telegram 跳转与导航体验。 |
| v0.5.1-message-refresh-copy | 有 | 消息权限刷新、文本选择与全文复制。 |
| v0.6.0-scroll-pagination-fix | 有 | 长消息滚动与历史分页游标优化。 |
| v0.7.0-continuous-sync | 有 | 持续历史同步与分页进度显示。 |

## v0.7.0-continuous-sync

采用连续历史分页策略：以每页最早消息 ID 作为后续请求游标，使用 `chatId + messageId` 对重叠消息去重。Telegram 返回空页时完成当前会话同步；重复页或游标暂未推进时，应用会继续请求并显示页码进度。

## v0.6.0-scroll-pagination-fix

消息详情页支持整页垂直滚动。历史同步优化分页游标和重复页处理，提升连续读取更早消息的稳定性。

## v0.5.1-message-refresh-copy

消息操作前刷新目标消息及权限属性。详情页支持系统文本选择和全文复制，并在可用时提供转发与 Telegram 链接操作。

## v0.5.0-message-actions

消息详情增加 Saved Messages 转发与“在 Telegram 中打开”。搜索结果和详情页之间保留原有列表位置，Android 返回键优先关闭详情页。

## v0.4.0-chat-picker

同步页面支持会话名称检索、已选会话置顶和已选数量显示，便于管理同步范围。

## v0.3.2-sync-icon

完善会话标题加载和历史同步逻辑，媒体消息的 caption 可进入本地索引，并更新深靛蓝对话检索主题启动图标。

## v0.3.1-history-icon

完成启动图标设计迭代；相关视觉改动已包含在 v0.3.2。

## v0.3.0-full-history

引入已选会话的分页历史读取，为本地历史消息索引提供连续同步能力。

## v0.2.1-code-retry

完善 `PHONE_CODE_INVALID`、`PHONE_CODE_EXPIRED` 和 `PHONE_NUMBER_INVALID` 等认证状态的错误提示与恢复路径。

## v0.2.0-tdlib

引入 TDLib 原生登录状态机，包括手机号、验证码与两步验证，并实现会话选择、文本同步和本地 SQLite 检索。

## v0.1.0-local-import

提供 Telegram 导出 JSON / JSONL 的本地导入、索引和关键词检索能力；不包含 Telegram 登录流程。
