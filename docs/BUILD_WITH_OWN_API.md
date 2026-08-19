# 使用自己的 Telegram API 构建

Telegram Search Native 的公开源码不包含任何默认的 Telegram API ID 或 API Hash。克隆仓库后，你可以选择在应用“设置”页面手动填写，也可以在**构建时**注入自己的参数，使生成的 APK 首次启动即可进入 Telegram 登录流程。

> `TG_DEFAULT_API_HASH` 是构建配置，不应提交到 Git、截图、Issue、构建日志或公开聊天中。`local.properties` 已被 Git 忽略。

## 方式一：本地 `local.properties`

在项目根目录创建或编辑 `local.properties`。保留已有的 Android SDK 配置，并追加自己的 Telegram 参数：

```properties
sdk.dir=/path/to/android-sdk
TG_DEFAULT_API_ID=12345678
TG_DEFAULT_API_HASH=replace_with_your_own_hash
```

然后构建：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

## 方式二：环境变量

此方式适用于本机终端、受保护的 CI 密钥库或私有构建机，不需要将参数写入文件：

```bash
export TG_DEFAULT_API_ID=12345678
export TG_DEFAULT_API_HASH=replace_with_your_own_hash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

也可传入同名 Gradle 属性：

```bash
./gradlew :app:assembleDebug \
  -PTG_DEFAULT_API_ID=12345678 \
  -PTG_DEFAULT_API_HASH=replace_with_your_own_hash
```

参数优先级为：**环境变量 → Gradle 属性 → `local.properties`**。

## 应用内手动覆盖

构建时注入的 API 仅是默认值。应用首次运行后，仍可在“设置”页面输入自己的 API ID 与 API Hash；保存后会优先使用 Android Keystore 支撑的加密偏好中的设备本地自定义参数。设置页的 API ID 与 API Hash 默认遮蔽，可使用末尾的小眼睛临时显示或隐藏。

若构建时没有提供默认参数，应用行为保持不变：首次连接前会提示在“设置”中手动填写自己的 API 参数。

## 面向公开发布的提醒

如果你要为自己的公开发行版配置默认 API，应通过 Telegram Developer Portal 为自己的客户端取得参数，并用受保护的构建密钥注入。不要复用其他项目的 API，也不要将生产参数提交到公开仓库。Android APK 运行时仍需使用这些客户端配置，因此“未提交到 Git”不代表它可以被视为绝对不可提取的长期秘密。
