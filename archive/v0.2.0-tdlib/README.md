# Telegram Search Native (Android)

这是一个 Kotlin + Jetpack Compose 的设备内 Telegram 客户端原型。它参考 `groupultra/telegram-search` 的“登录、同步、索引、搜索”分层，但改用 TDLib 在手机本地建立 Telegram 会话和消息缓存。应用不调用作者的线上网页或服务端。

## 可用能力

应用提供 API 参数的设备内加密配置、TDLib 授权状态机（手机号、验证码、2FA）、会话选择、每会话最近 100 条文本消息同步、本地 SQLite 搜索、收藏和清除索引。API 参数由 Android Keystore 支撑的加密偏好保存；TDLib 会话数据库与检索索引位于应用私有目录，且 Android 备份已禁用；媒体默认不下载。

## 第一次使用

1. 从 `my.telegram.org/apps` 创建你自己的 Telegram application，并获得 API ID 与 API Hash。
2. 在应用“设置”页保存参数，打开“连接”页按 Telegram 返回的状态输入手机号、验证码或两步验证密码。
3. 连接成功后，在“同步”页勾选会话，点击同步；随后可在“搜索”页进行离线检索。

## 编译

工程默认使用 JitPack 的 `com.github.tdlibx:td:1.8.56` 工件。首次构建需要访问 JitPack/Maven 解析 TDLib JNI 依赖；若企业发布需要更严格的供应链，请改为从官方 TDLib 源码针对所需 ABI 构建并审核 `libtdjni.so`。

```bash
./gradlew :app:assembleDebug
```

## 重要限制

这是个人测试原型，不应绕过 Telegram 的安全确认、登录限流或开发者条款。请勿共享 API Hash、验证码、两步验证密码或应用数据目录。TDLib 工作数据库使用私有沙盒、禁用 Android 备份和设备锁作为当前保护边界；生产发布前应锁定依赖、配置发布签名、验证 TDLib 数据库加密、完成隐私政策和删除数据流程，并核对 Telegram API 条款。
