# Telegram Search Native v0.5.1：消息刷新与文本复制

v0.5.1 改进了历史消息的操作前校验流程，并为消息详情提供系统级文本选择与全文复制能力。

## 功能更新

| 功能 | 说明 |
|---|---|
| 消息状态刷新 | 执行转发或链接操作前，重新获取目标消息及其当前权限属性。 |
| 转发至 Saved Messages | 在 Telegram 允许转发时，将原消息转发至当前账号的 Saved Messages。 |
| 在 Telegram 中打开 | 在 Telegram 提供可用链接时，通过系统打开该消息链接。 |
| 文本选择 | 消息详情正文支持 Android 系统文本选择菜单。 |
| 复制全文 | 将已同步消息全文复制到系统剪贴板。 |

## 使用方式

安装 `TelegramSearchNative-v0.5.1-message-refresh-copy-debug.apk` 后，在消息详情页使用“复制全文”、文本长按选择、“转发到 Telegram 收藏夹”或“在 Telegram 中打开”。

## 访问范围

消息的转发和链接可用性以 Telegram 当前返回的消息属性为准。私密消息、受内容保护限制的消息或没有公开链接的消息可能无法完成对应操作。应用仅使用当前账号的 TDLib 会话，不绕过 Telegram 的权限和内容保护规则。[1] [2] [3] [4]

## References

[1] [TDLib getMessage](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message.html)

[2] [TDLib getMessageProperties](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_properties.html)

[3] [TDLib forwardMessages](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forward_messages.html)

[4] [TDLib getMessageLink](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_link.html)
