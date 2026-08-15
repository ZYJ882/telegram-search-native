# Telegram Search Native v0.5.0：消息操作与返回体验更新

## 本次更新

新版在消息详情页增加了两个账号级操作，并修复了详情返回后的列表位置与手机返回键行为。

| 功能 | 使用方式 | 结果与边界 |
|---|---|---|
| 转发到 Telegram 收藏夹 | 打开一条已同步消息，点击 **转发到 Telegram 收藏夹**。 | App 通过设备内 TDLib 将原消息转发给当前登录账号自己的 Saved Messages；若原频道开启禁止转发/保存保护，Telegram 可能拒绝该操作。 |
| 在 Telegram 中打开 | 在消息详情点击 **在 Telegram 中打开**。 | App 请求 TDLib 为该消息生成 HTTPS Telegram 链接，并调用手机系统打开。若安装了官方 Telegram，通常会由它处理；没有可用链接的私密、受保护或限制消息会显示原因。 |
| 搜索页返回位置 | 从搜索结果打开详情后，点击顶部“返回”或按手机系统返回键。 | 回到原先阅读位置，不会重新跳到列表第一个结果。 |
| 系统返回键 | 在消息详情按手机返回键。 | 优先关闭详情并回到搜索列表；详情已关闭时，系统返回行为保持 Android 默认逻辑。 |

## 使用步骤

1. 直接安装 `TelegramSearchNative-v0.5.0-message-actions-debug.apk` 覆盖旧版。请不要先卸载旧版，以保留设备本地的会话、API 参数和搜索索引。
2. 在 **搜索** 页打开一条结果。
3. 若需要保存在 Telegram 官方收藏夹，点击“转发到 Telegram 收藏夹”。操作成功后，应用会显示“已转发到 Telegram 收藏夹”。
4. 若需要回到原频道的原始消息，点击“在 Telegram 中打开”。若应用显示无法生成链接，表示该消息不具备公开/可访问的 Telegram 消息链接；此时可以继续使用前一项收藏转发功能。
5. 用手机返回键测试：应该先退出详情页并回到此前滚动位置。

> “本机书签”与“Telegram 收藏夹”是两件事。本机书签只标记设备 SQLite 索引中的一条记录；Telegram 收藏夹会向你的 Telegram 账号发送一次真实转发请求。

## 技术与隐私边界

转发与生成链接均在登录用户设备上的 TDLib 会话内执行，不会把验证码、两步验证密码、`api_hash` 或消息正文上传到本应用的外部服务器。Telegram 是否允许转发或生成消息链接由消息属性和原会话权限决定；应用不会绕过频道的内容保护规则。[1] [2]

## References

[1]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forward_messages.html "TDLib forwardMessages"
[2]: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_message_link.html "TDLib getMessageLink"
