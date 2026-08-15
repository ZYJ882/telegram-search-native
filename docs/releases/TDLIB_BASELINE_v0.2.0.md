# Telegram Search Native v0.2.0：TDLib 原生客户端

v0.2.0 引入基于 TDLib 的 Android 原生 Telegram 客户端能力，在设备内完成账号授权、会话选择、消息同步和离线检索。

## 功能

| 功能 | 说明 |
|---|---|
| Telegram 授权 | 支持手机号、验证码和两步验证的 TDLib 授权流程。 |
| API 参数管理 | 支持在应用设置中保存 Telegram API ID 与 API Hash。 |
| 会话选择 | 可从已加载的会话中选择同步目标。 |
| 文本同步 | 将可访问的文本消息写入设备内 SQLite 索引。 |
| 本地搜索 | 支持对已同步消息进行关键词检索和本机书签标记。 |

## 数据范围

Telegram 登录状态、同步索引和应用配置均保存在 Android 应用私有目录。可读取内容受 Telegram 服务端、账号权限和会话访问范围限制。

## 构建信息

最低 Android 版本为 API 26。APK 为调试签名构建，用于本地安装与功能体验。
