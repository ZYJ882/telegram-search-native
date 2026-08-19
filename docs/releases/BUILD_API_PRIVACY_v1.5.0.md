# Telegram Search Native v1.5.0：自定义 API 构建与凭据显示控制

v1.5.0 为公开源码增加可选的 Telegram API 构建注入方式，并完善设置页中 API ID 与 API Hash 的可见性控制。

## 功能更新

| 项目 | 说明 |
|---|---|
| 本地构建参数 | 可在受 Git 忽略的 `local.properties` 中提供 `TG_DEFAULT_API_ID` 与 `TG_DEFAULT_API_HASH`。 |
| 环境变量构建 | 支持通过同名环境变量或 Gradle 属性注入默认 API，适用于本机终端和受保护的构建环境。 |
| 参数优先级 | 环境变量优先于 Gradle 属性，Gradle 属性优先于 `local.properties`。 |
| 手动覆盖 | 设置页中手动保存的 API 参数继续通过 Android Keystore 支撑的加密偏好保存，并优先于构建默认值。 |
| 凭据遮蔽 | API ID 与 API Hash 默认以遮蔽形式显示；可点击输入框末尾小眼睛临时显示或隐藏。 |
| 无默认配置兼容 | 不提供构建参数时，应用保持原有行为，首次连接前仍提示在设置页手动填写 API。 |

## 自行构建

在项目根目录的 `local.properties` 中追加：

```properties
TG_DEFAULT_API_ID=12345678
TG_DEFAULT_API_HASH=replace_with_your_own_hash
```

或在构建前设置同名环境变量。完整说明见仓库的 [使用自己的 Telegram API 构建](../BUILD_WITH_OWN_API.md)。

> 不要将真实 API Hash 提交到 Git、Issue、截图、构建日志或公开聊天中。构建默认值仅用于自己的 APK；公开发布应用应使用由发布维护者负责的独立项目 API。
