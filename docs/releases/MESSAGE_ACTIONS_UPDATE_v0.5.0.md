# Telegram Search Native v0.5.0：消息操作与导航体验

v0.5.0 为消息详情页增加 Telegram 操作入口，并改善搜索结果与消息详情之间的返回体验。

## 功能更新

| 功能 | 说明 |
|---|---|
| 转发至 Saved Messages | 通过设备内 TDLib 将已同步消息转发至当前 Telegram 账号的 Saved Messages。 |
| 在 Telegram 中打开 | 为支持外链的消息生成 Telegram HTTPS 链接，并交由系统处理。 |
| 搜索位置恢复 | 从搜索结果进入详情后返回，保留原有列表滚动位置。 |
| 系统返回行为 | Android 返回键优先关闭消息详情，再执行页面级返回。 |

## 使用方式

安装 `TelegramSearchNative-v0.5.0-message-actions-debug.apk` 后，在**搜索**页面打开任意已索引消息。消息详情页提供“转发到 Telegram 收藏夹”和“在 Telegram 中打开”操作。

本机书签仅标记本地 SQLite 索引中的记录；Saved Messages 操作会向当前 Telegram 账号发起一次转发请求。

## 权限与访问范围

转发及消息链接生成均在设备内 TDLib 会话中执行。Telegram 是否允许转发、保存或生成消息链接取决于消息属性、会话权限和内容保护策略。应用不会绕过 Telegram 的访问控制规则。[1] [2]

## References

[1] [TDLib forwardMessages](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forward_messages.html)

[2] [TDLib getMessageLink](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_link.html)
