package app.lingogram.tgsearchnative

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.ArrayDeque

sealed class AuthStage(val title: String, val detail: String) {
    data object NeedConfig : AuthStage("配置 Telegram API", "在设置页填写你自己的 API ID 与 API Hash。")
    data object Starting : AuthStage("正在初始化", "正在打开设备内 TDLib 数据库。")
    data object NeedPhone : AuthStage("输入手机号", "使用含国家/地区码的格式，例如 +8613800000000。")
    data object NeedCode : AuthStage("输入验证码", "验证码由 Telegram 指定的方式发送。")
    data object NeedPassword : AuthStage("两步验证", "此账号启用了 Telegram 额外密码。")
    data object NeedOtherDevice : AuthStage("在其他设备确认", "请在已有 Telegram 设备上打开确认链接。")
    data object Ready : AuthStage("已连接", "会话与索引只保存在当前手机。")
    data class Failed(val reason: String) : AuthStage("连接失败", reason)
}

data class RemoteChat(val id: Long, val title: String, val selected: Boolean = false)

class TdLibGateway(
    private val context: Context,
    private val secure: SecureSettings,
    private val onIndex: (List<LocalMessage>, (Int) -> Unit) -> Unit,
    private val onActionNotice: (String) -> Unit
) {
    companion object { private const val HISTORY_PAGE_SIZE = 100; private const val HISTORY_RETRY_DELAY_MS = 900L }

    var stage by mutableStateOf<AuthStage>(if (secure.apiConfig() == null) AuthStage.NeedConfig else AuthStage.Starting)
        private set
    var chats by mutableStateOf(emptyList<RemoteChat>())
        private set
    var syncNote by mutableStateOf("等待连接")
        private set
    var recoveryHint by mutableStateOf<String?>(null)
        private set
    var syncInProgress by mutableStateOf(false)
        private set
    var syncProcessedMessages by mutableStateOf(0)
        private set
    var syncIndexedMessages by mutableStateOf(0)
        private set
    var syncCurrentChat by mutableStateOf<String?>(null)
        private set
    var syncFinishedChats by mutableStateOf(0)
        private set
    var syncTotalChats by mutableStateOf(0)
        private set
    var syncPagesFetched by mutableStateOf(0)
        private set

    private var client: Client? = null
    private var activeApiSource = ApiConfigSource.NONE
    private val main = Handler(Looper.getMainLooper())
    private val handler = Client.ResultHandler { obj -> main.post { handle(obj) } }
    private val syncQueue = ArrayDeque<RemoteChat>()
    private var activeSyncChat: RemoteChat? = null
    private var nextFromMessageId = 0L
    private val seenHistoryMessageIds = mutableSetOf<Long>()
    private var repeatedHistoryPages = 0
    private val pendingTitleLoads = mutableSetOf<Long>()
    private var cancelRequested = false

    fun start() {
        activeApiSource = secure.apiConfigSource()
        val cfg = secure.apiConfig() ?: run { stage = AuthStage.NeedConfig; return }
        if (client != null) return
        try {
            client = Client.create(handler, null, null)
            stage = AuthStage.Starting
        } catch (e: Throwable) {
            stage = AuthStage.Failed("TDLib 原生库无法加载：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun restart() {
        cancelSync("已停止同步，正在重新初始化登录")
        recoveryHint = null
        client?.send(TdApi.Close(), handler)
        client = null
        chats = emptyList()
        start()
    }

    fun sendPhone(value: String) {
        recoveryHint = null
        client?.send(TdApi.SetAuthenticationPhoneNumber(value.trim(), null), handler)
    }

    fun sendCode(value: String) {
        recoveryHint = null
        client?.send(TdApi.CheckAuthenticationCode(value.trim()), handler)
    }

    fun sendPassword(value: String) {
        client?.send(TdApi.CheckAuthenticationPassword(value), handler)
    }

    private fun refreshMessageProperties(message: LocalMessage, afterRefresh: (TdApi.MessageProperties) -> Unit) {
        client?.send(TdApi.GetMessage(message.chatId, message.remoteId), Client.ResultHandler { messageResult -> main.post {
            if (messageResult !is TdApi.Message) {
                val reason = (messageResult as? TdApi.Error)?.message ?: "原消息不可访问"
                onActionNotice("无法刷新这条原消息：$reason")
                return@post
            }
            client?.send(TdApi.GetMessageProperties(message.chatId, message.remoteId), Client.ResultHandler { propertiesResult -> main.post {
                val properties = propertiesResult as? TdApi.MessageProperties
                if (properties == null) {
                    val reason = (propertiesResult as? TdApi.Error)?.message ?: "无法读取消息权限"
                    onActionNotice("无法确认消息操作权限：$reason")
                    return@post
                }
                afterRefresh(properties)
            } })
        } })
    }

    fun forwardToSavedMessages(message: LocalMessage) {
        if (stage !is AuthStage.Ready) {
            onActionNotice("请先连接 Telegram，再转发到收藏夹")
            return
        }
        refreshMessageProperties(message) { properties ->
            if (!properties.canBeForwarded) {
                onActionNotice("Telegram 当前不允许转发这条消息；可能是频道内容保护或权限限制")
                return@refreshMessageProperties
            }
            client?.send(TdApi.GetMe(), Client.ResultHandler { meResult -> main.post {
                val me = meResult as? TdApi.User
                if (me == null) {
                    onActionNotice("无法取得当前 Telegram 账号，稍后重试")
                    return@post
                }
                client?.send(TdApi.CreatePrivateChat(me.id, false), Client.ResultHandler { chatResult -> main.post {
                    val savedChat = chatResult as? TdApi.Chat
                    if (savedChat == null) {
                        onActionNotice("无法打开 Telegram 收藏夹")
                        return@post
                    }
                    client?.send(
                        TdApi.ForwardMessages(savedChat.id, null, message.chatId, longArrayOf(message.remoteId), null, false, false),
                        Client.ResultHandler { forwarded -> main.post {
                            val result = forwarded as? TdApi.Messages
                            if (forwarded is TdApi.Error) {
                                onActionNotice("转发到收藏夹失败：${forwarded.message}")
                            } else if (result?.messages?.any { it != null } == true) {
                                onActionNotice("已转发到 Telegram 收藏夹")
                            } else {
                                onActionNotice("Telegram 未接受该消息的转发请求")
                            }
                        } }
                    )
                } })
            } })
        }
    }

    fun openMessageInTelegram(message: LocalMessage) {
        if (stage !is AuthStage.Ready) {
            onActionNotice("请先连接 Telegram，再打开原消息")
            return
        }
        refreshMessageProperties(message) { properties ->
            if (!properties.canGetLink) {
                onActionNotice("Telegram 当前未为该消息提供跳转链接；私密或受保护消息可能没有外链")
                return@refreshMessageProperties
            }
            client?.send(
                TdApi.GetMessageLink(message.chatId, message.remoteId, 0, false, false),
                Client.ResultHandler { result -> main.post {
                    val link = result as? TdApi.MessageLink
                    if (link == null || link.link.isBlank()) {
                        val reason = (result as? TdApi.Error)?.message ?: "此消息没有可用的 Telegram 跳转链接"
                        onActionNotice("无法在 Telegram 打开：$reason")
                        return@post
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Throwable) {
                        onActionNotice("未找到可打开 Telegram 链接的应用")
                    }
                } }
            )
        }
    }

    fun toggleChat(id: Long) {
        if (syncInProgress) return
        chats = chats.map { if (it.id == id) it.copy(selected = !it.selected) else it }
    }

    fun syncSelected() {
        if (syncInProgress) return
        val selected = chats.filter { it.selected }
        if (selected.isEmpty()) {
            syncNote = "请至少选择一个会话"
            return
        }
        cancelRequested = false
        syncQueue.clear()
        syncQueue.addAll(selected)
        syncInProgress = true
        syncProcessedMessages = 0
        syncIndexedMessages = 0
        syncFinishedChats = 0
        syncTotalChats = selected.size
        syncPagesFetched = 0
        syncCurrentChat = null
        syncNextChat()
    }

    fun cancelSync(message: String = "已请求取消同步") {
        if (!syncInProgress) return
        cancelRequested = true
        syncNote = "$message；当前页处理完成后停止。"
    }

    private fun syncNextChat() {
        if (cancelRequested) {
            finishSync("同步已取消：已扫描 $syncProcessedMessages 条消息，新增 $syncIndexedMessages 条文本索引。")
            return
        }
        val next = if (syncQueue.isEmpty()) null else syncQueue.removeFirst()
        if (next == null) {
            finishSync("同步完成：扫描 $syncProcessedMessages 条消息，新增 $syncIndexedMessages 条文本索引。")
            return
        }
        activeSyncChat = next
        nextFromMessageId = 0L
        seenHistoryMessageIds.clear()
        repeatedHistoryPages = 0
        syncCurrentChat = next.title
        syncNote = "正在同步「${next.title}」的全部可访问历史文本…"
        requestHistoryPage()
    }

    private fun requestHistoryPage() {
        val chat = activeSyncChat ?: return
        if (cancelRequested) {
            syncNextChat()
            return
        }
        // TDLib's documented continuous-history pattern is to reuse the last received message id
        // as from_message_id. One overlapping message is expected and is removed locally by id.
        client?.send(
            TdApi.GetChatHistory(chat.id, nextFromMessageId, 0, HISTORY_PAGE_SIZE, false),
            Client.ResultHandler { obj -> main.post { handleHistoryPage(chat, obj) } }
        )
    }

    private fun handleHistoryPage(chat: RemoteChat, obj: TdApi.Object) {
        if (!syncInProgress || activeSyncChat?.id != chat.id) return
        if (obj is TdApi.Error) {
            syncFinishedChats++
            syncNote = "「${chat.title}」同步失败：${obj.message}；将继续下一个会话。"
            syncNextChat()
            return
        }
        val page = obj as? TdApi.Messages ?: run {
            syncFinishedChats++
            syncNote = "「${chat.title}」未返回历史消息；将继续下一个会话。"
            syncNextChat()
            return
        }
        val remoteMessages = page.messages
        syncPagesFetched++
        if (remoteMessages.isEmpty()) {
            syncFinishedChats++
            syncNote = "「${chat.title}」已到达 Telegram 返回的可访问历史末尾：共扫描 $syncProcessedMessages 条。"
            syncNextChat()
            return
        }
        val freshMessages = remoteMessages.filter { seenHistoryMessageIds.add(it.id) }
        syncProcessedMessages += freshMessages.size
        val pageOldestId = remoteMessages.lastOrNull()?.id ?: 0L
        val cursorAdvanced = nextFromMessageId == 0L || (pageOldestId != 0L && pageOldestId < nextFromMessageId)
        val mapped = freshMessages.mapNotNull { toLocal(it, chat) }
        onIndex(mapped) { newlyInserted ->
            if (!syncInProgress || activeSyncChat?.id != chat.id) return@onIndex
            syncIndexedMessages += newlyInserted
            if (cancelRequested) {
                syncFinishedChats++
                syncNextChat()
            } else if (cursorAdvanced) {
                nextFromMessageId = pageOldestId
                repeatedHistoryPages = 0
                syncNote = "正在同步「${chat.title}」：第 $syncPagesFetched 页，已扫描 $syncProcessedMessages 条，新增 $syncIndexedMessages 条文本索引。"
                main.postDelayed({ requestHistoryPage() }, 80L)
            } else {
                repeatedHistoryPages++
                syncNote = "「${chat.title}」第 $syncPagesFetched 页与上一页重复，仍在请求更早历史（第 $repeatedHistoryPages 次重试）；可随时手动停止。"
                main.postDelayed({ requestHistoryPage() }, HISTORY_RETRY_DELAY_MS)
            }
        }
    }

    private fun finishSync(message: String) {
        syncInProgress = false
        activeSyncChat = null
        syncCurrentChat = null
        syncQueue.clear()
        syncNote = message
    }

    fun logout() {
        cancelSync("已停止同步")
        client?.send(TdApi.LogOut(), handler)
        chats = emptyList()
        stage = AuthStage.NeedConfig
    }

    private fun handle(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuth(obj.authorizationState)
            is TdApi.UpdateNewChat -> upsert(obj.chat.id, obj.chat.title)
            is TdApi.UpdateChatTitle -> upsert(obj.chatId, obj.title)
            is TdApi.Error -> handleError(obj)
        }
    }

    private fun handleError(error: TdApi.Error) {
        if (stage is AuthStage.Starting && isApiCredentialError(error.message)) {
            recoveryHint = null
            stage = AuthStage.Failed(apiCredentialFailureMessage(error.message))
            return
        }
        when (error.message) {
            "PHONE_CODE_INVALID" -> {
                stage = AuthStage.NeedCode
                recoveryHint = "验证码无效：请核对本次登录请求最新收到的验证码后重试；不要使用旧验证码。若仍失败，请重新开始登录以请求新验证码。"
            }
            "PHONE_CODE_EXPIRED" -> {
                stage = AuthStage.NeedPhone
                recoveryHint = "验证码已过期：请重新开始登录，再输入手机号以请求新验证码。"
            }
            "PHONE_NUMBER_INVALID" -> {
                stage = AuthStage.NeedPhone
                recoveryHint = "手机号格式无效：请使用含国家/地区码的完整格式，例如 +86…"
            }
            else -> {
                recoveryHint = null
                stage = AuthStage.Failed(error.message)
            }
        }
    }

    private fun isApiCredentialError(message: String): Boolean {
        val code = message.uppercase()
        return code.contains("API_ID") || code.contains("API_HASH") || code.contains("API KEY")
    }

    private fun apiCredentialFailureMessage(raw: String): String = when (activeApiSource) {
        ApiConfigSource.CUSTOM -> "设备内自定义 API 无法使用（$raw）。请在设置页核对并重新保存自己的 API ID 与 API Hash；若此版本含构建默认 API，可清除自定义参数后恢复默认配置。"
        ApiConfigSource.BUNDLED -> "构建默认 API 无法使用（$raw）。请联系此 APK 的构建者更新默认参数，或在设置页填写自己的 API ID 与 API Hash。"
        ApiConfigSource.NONE -> "未找到可用 Telegram API 参数。请在设置页填写自己的 API ID 与 API Hash。"
    }

    private fun onAuth(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                activeApiSource = secure.apiConfigSource()
                val cfg = secure.apiConfig() ?: run { stage = AuthStage.NeedConfig; return }
                val p = TdApi.SetTdlibParameters()
                p.databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                p.useMessageDatabase = true
                p.useChatInfoDatabase = true
                p.useFileDatabase = false
                p.useSecretChats = true
                p.apiId = cfg.apiId
                p.apiHash = cfg.apiHash
                p.systemLanguageCode = "zh"
                p.deviceModel = "Android"
                p.applicationVersion = BuildConfig.VERSION_NAME
                client?.send(p, handler)
                stage = AuthStage.Starting
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> stage = AuthStage.NeedPhone
            is TdApi.AuthorizationStateWaitCode -> stage = AuthStage.NeedCode
            is TdApi.AuthorizationStateWaitPassword -> stage = AuthStage.NeedPassword
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> stage = AuthStage.NeedOtherDevice
            is TdApi.AuthorizationStateReady -> {
                stage = AuthStage.Ready
                client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 100), handler)
            }
            else -> stage = AuthStage.Failed("未处理的授权状态：${s.javaClass.simpleName}")
        }
    }

    private fun upsert(id: Long, title: String) {
        val old = chats.firstOrNull { it.id == id }
        val suppliedTitle = title.trim()
        if (suppliedTitle.isBlank() && pendingTitleLoads.add(id)) {
            client?.send(TdApi.GetChat(id), Client.ResultHandler { obj -> main.post {
                pendingTitleLoads.remove(id)
                if (obj is TdApi.Chat) upsert(obj.id, obj.title)
            } })
        }
        val safeTitle = suppliedTitle.ifBlank { old?.title?.takeIf { it.isNotBlank() } ?: "会话 $id（正在载入名称）" }
        chats = if (old == null) {
            (chats + RemoteChat(id, safeTitle)).sortedBy { it.title }
        } else {
            chats.map { if (it.id == id) it.copy(title = safeTitle) else it }
        }
    }

    private fun toLocal(message: TdApi.Message, chat: RemoteChat): LocalMessage? {
        val rawText = when (val content = message.content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> content.caption.text
            is TdApi.MessageVideo -> content.caption.text
            is TdApi.MessageAnimation -> content.caption.text
            is TdApi.MessageDocument -> content.caption.text
            is TdApi.MessageAudio -> content.caption.text
            is TdApi.MessageVoiceNote -> content.caption.text
            else -> null
        } ?: return null
        val text = rawText.trim()
        if (text.isBlank()) return null
        return LocalMessage(
            remoteId = message.id,
            chatId = chat.id,
            chatName = chat.title,
            sender = "Telegram",
            date = epochText(message.date),
            text = text
        )
    }
}
