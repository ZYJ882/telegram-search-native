# Telegram Search Native v0.5.1：历史消息操作修复与复制功能

## 你遇到的问题与本次修复

此前版本把同步时记录的会话 ID 和消息 ID 直接用于转发及生成外链。对于较早的频道历史，TDLib 有时尚未在当前会话内载入完整的消息对象与权限属性，导致应用错误显示“不能收藏”或“没有 Telegram 链接”，即使官方 Telegram 客户端可继续操作。

新版在执行操作前，先调用 TDLib 重新取得原消息，再取得该消息当前的权限属性，最后执行转发或消息链接请求。这样可以避免用过期的本地索引状态直接决定消息是否可操作。[1] [2]

| 功能 | v0.5.1 的行为 |
|---|---|
| 转发到 Telegram 收藏夹 | 先刷新消息和权限；若 Telegram 报告可转发，再把原消息转发至当前账号的 Saved Messages。若仍失败，提示显示 TDLib 返回的实际原因。 |
| 在 Telegram 中打开 | 先刷新消息和权限；仅在 Telegram 当前允许为该消息生成链接时打开系统链接。私密、受保护或 Telegram 不提供外链的消息仍无法由任何第三方客户端可靠生成外链。 |
| 自由选择文字 | 在详情页长按正文，可拖动选择范围并使用系统复制菜单。 |
| 复制全文 | 点击 **复制全文**，整段已同步文本进入 Android 系统剪贴板。 |

## 安装和测试

请直接安装 `TelegramSearchNative-v0.5.1-message-refresh-copy-debug.apk` 覆盖旧版，不要卸载旧版。打开一条此前失败的消息，先测试“复制全文”，再点击“转发到 Telegram 收藏夹”或“在 Telegram 中打开”。等待提示出现后再重复操作，避免对同一消息连续发出多个转发请求。

> 即使官方 Telegram 显示该消息可转发，Telegram 服务端仍是最终的权限判断者。该版本会刷新并采用当前 TDLib 返回的权限，但不会、也不能绕过频道的内容保护规则。

## References

[1]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message.html "TDLib getMessage"
[2]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_properties.html "TDLib getMessageProperties"
[3]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forward_messages.html "TDLib forwardMessages"
[4]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_link.html "TDLib getMessageLink"
