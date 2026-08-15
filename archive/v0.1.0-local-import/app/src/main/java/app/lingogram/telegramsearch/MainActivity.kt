package app.lingogram.telegramsearch

import android.app.Application
import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TelegramSearchLocalApp() }
    }
}

enum class Destination(val title: String) { SEARCH("搜索"), IMPORT("导入"), SAVED("收藏"), SETTINGS("设置") }

data class ArchiveMessage(
    val localId: Long = 0,
    val sourceId: String,
    val chatName: String,
    val sender: String,
    val sentAt: String,
    val body: String,
    val bookmarked: Boolean = false
)

data class ImportReport(val scanned: Int, val indexed: Int, val skipped: Int, val chats: Int)

data class ArchiveStats(val messages: Int, val chats: Int, val saved: Int)

class ArchiveDb(context: Context) : SQLiteOpenHelper(context, "telegram_search_local.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id TEXT NOT NULL,
            chat_name TEXT NOT NULL,
            sender TEXT NOT NULL,
            sent_at TEXT NOT NULL,
            body TEXT NOT NULL,
            normalized_body TEXT NOT NULL,
            bookmarked INTEGER NOT NULL DEFAULT 0,
            UNIQUE(source_id, chat_name) ON CONFLICT IGNORE
        )""".trimIndent())
        db.execSQL("CREATE INDEX idx_messages_search ON messages(normalized_body)")
        db.execSQL("CREATE INDEX idx_messages_chat_date ON messages(chat_name, sent_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertAll(messages: List<ArchiveMessage>): Int {
        var inserted = 0
        writableDatabase.beginTransaction()
        try {
            messages.forEach { message ->
                val statement = writableDatabase.compileStatement(
                    "INSERT OR IGNORE INTO messages(source_id, chat_name, sender, sent_at, body, normalized_body) VALUES(?,?,?,?,?,?)"
                )
                statement.bindString(1, message.sourceId)
                statement.bindString(2, message.chatName)
                statement.bindString(3, message.sender)
                statement.bindString(4, message.sentAt)
                statement.bindString(5, message.body)
                statement.bindString(6, message.body.lowercase(Locale.ROOT))
                if (statement.executeInsert() != -1L) inserted++
                statement.close()
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return inserted
    }

    fun search(query: String, chat: String?, from: String, until: String): List<ArchiveMessage> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (query.isNotBlank()) { where += "normalized_body LIKE ?"; args += "%${query.lowercase(Locale.ROOT)}%" }
        if (!chat.isNullOrBlank()) { where += "chat_name = ?"; args += chat }
        if (from.isNotBlank()) { where += "sent_at >= ?"; args += from }
        if (until.isNotBlank()) { where += "sent_at <= ?"; args += until + "T23:59:59" }
        val cursor = readableDatabase.query("messages", null, where.joinToString(" AND ").ifBlank { null }, args.toTypedArray(), null, null, "sent_at DESC", "250")
        return cursor.use { c -> generateSequence { if (c.moveToNext()) c else null }.map { row(it) }.toList() }
    }

    fun bookmarks(): List<ArchiveMessage> = readableDatabase.query("messages", null, "bookmarked=1", null, null, null, "sent_at DESC").use { c ->
        generateSequence { if (c.moveToNext()) c else null }.map { row(it) }.toList()
    }

    fun chats(): List<String> = readableDatabase.rawQuery("SELECT DISTINCT chat_name FROM messages ORDER BY chat_name COLLATE NOCASE", null).use { c ->
        generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
    }

    fun contextOf(message: ArchiveMessage): List<ArchiveMessage> = readableDatabase.query(
        "messages", null, "chat_name=? AND sent_at BETWEEN ? AND ?", arrayOf(message.chatName, shiftMinutes(message.sentAt, -120), shiftMinutes(message.sentAt, 120)), null, null, "sent_at ASC", "60"
    ).use { c -> generateSequence { if (c.moveToNext()) c else null }.map { row(it) }.toList() }

    fun toggleBookmark(id: Long): Boolean {
        val current = readableDatabase.rawQuery("SELECT bookmarked FROM messages WHERE id=?", arrayOf(id.toString())).use { it.moveToFirst() && it.getInt(0) == 1 }
        writableDatabase.execSQL("UPDATE messages SET bookmarked=? WHERE id=?", arrayOf(if (current) 0 else 1, id))
        return !current
    }

    fun stats(): ArchiveStats = readableDatabase.rawQuery("SELECT COUNT(*), COUNT(DISTINCT chat_name), SUM(bookmarked) FROM messages", null).use {
        it.moveToFirst(); ArchiveStats(it.getInt(0), it.getInt(1), if (it.isNull(2)) 0 else it.getInt(2))
    }

    fun clear() { writableDatabase.delete("messages", null, null) }

    private fun row(c: android.database.Cursor) = ArchiveMessage(
        localId = c.getLong(c.getColumnIndexOrThrow("id")), sourceId = c.getString(c.getColumnIndexOrThrow("source_id")),
        chatName = c.getString(c.getColumnIndexOrThrow("chat_name")), sender = c.getString(c.getColumnIndexOrThrow("sender")),
        sentAt = c.getString(c.getColumnIndexOrThrow("sent_at")), body = c.getString(c.getColumnIndexOrThrow("body")),
        bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1
    )

    private fun shiftMinutes(value: String, minutes: Long): String = try { Instant.parse(value).plusSeconds(minutes * 60).toString() } catch (_: Exception) { value }
}

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ArchiveDb(application)
    var destination by mutableStateOf(Destination.SEARCH)
    var query by mutableStateOf("")
    var selectedChat by mutableStateOf<String?>(null)
    var fromDate by mutableStateOf("")
    var untilDate by mutableStateOf("")
    var results by mutableStateOf(emptyList<ArchiveMessage>())
    var chats by mutableStateOf(emptyList<String>())
    var saved by mutableStateOf(emptyList<ArchiveMessage>())
    var stats by mutableStateOf(ArchiveStats(0, 0, 0))
    var selectedMessage by mutableStateOf<ArchiveMessage?>(null)
    var contextMessages by mutableStateOf(emptyList<ArchiveMessage>())
    var loading by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)

    init { refreshAll() }

    fun refreshAll() { search(); chats = db.chats(); saved = db.bookmarks(); stats = db.stats() }
    fun search() { results = db.search(query, selectedChat, fromDate, untilDate) }
    fun showMessage(message: ArchiveMessage) { selectedMessage = message; contextMessages = db.contextOf(message) }
    fun closeMessage() { selectedMessage = null; contextMessages = emptyList() }
    fun toggleBookmark(message: ArchiveMessage) { db.toggleBookmark(message.localId); refreshAll(); selectedMessage = results.firstOrNull { it.localId == message.localId } ?: saved.firstOrNull { it.localId == message.localId } ?: message.copy(bookmarked = !message.bookmarked) }

    fun importUri(uri: Uri) {
        loading = true
        viewModelScopeLaunch {
            val report = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val parsed = ArchiveImportParser.parse(input)
                    val indexed = db.insertAll(parsed)
                    ImportReport(parsed.size, indexed, parsed.size - indexed, parsed.map { it.chatName }.distinct().size)
                } ?: throw IllegalStateException("无法读取所选文件")
            }
            refreshAll(); loading = false; destination = Destination.SEARCH
            notice = "导入完成：扫描 ${report.scanned} 条，新增 ${report.indexed} 条，涉及 ${report.chats} 个会话。"
        }
    }

    fun addDemo() {
        loading = true
        viewModelScopeLaunch {
            withContext(Dispatchers.IO) { db.insertAll(ArchiveImportParser.demoMessages()) }
            refreshAll(); loading = false; destination = Destination.SEARCH; notice = "已载入 6 条示例消息，可立即体验搜索与收藏。"
        }
    }

    fun clearAll() { db.clear(); refreshAll(); notice = "本机索引已清除。" }
    private fun viewModelScopeLaunch(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: Exception) { loading = false; notice = "处理失败：${e.message ?: "文件格式不受支持"}" } } }
}

object ArchiveImportParser {
    fun parse(input: InputStream): List<ArchiveMessage> {
        val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText().trim()
        if (text.isBlank()) return emptyList()
        val objects = when {
            text.startsWith("{") -> messagesFromRoot(JSONObject(JSONTokener(text)))
            text.startsWith("[") -> JSONArray(JSONTokener(text)).let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
            else -> text.lineSequence().mapNotNull { line -> runCatching { JSONObject(line.trim()) }.getOrNull() }.toList()
        }
        return objects.mapNotNull { objectToMessage(it) }
    }

    private fun messagesFromRoot(root: JSONObject): List<JSONObject> {
        val direct = root.optJSONArray("messages") ?: root.optJSONArray("data") ?: return listOf(root)
        return (0 until direct.length()).mapNotNull { direct.optJSONObject(it) }
    }

    private fun objectToMessage(obj: JSONObject): ArchiveMessage? {
        val type = obj.optString("type", "message")
        if (type != "message" && obj.optString("text").isBlank() && obj.optString("body").isBlank()) return null
        val body = textOf(obj.opt("text")).ifBlank { textOf(obj.opt("body")) }.trim()
        if (body.isBlank()) return null
        val chat = obj.optString("chat_name").ifBlank { obj.optString("chatName") }.ifBlank { obj.optString("dialog_name") }.ifBlank {
            obj.optJSONObject("chat")?.optString("name") ?: "未命名会话"
        }
        val sender = obj.optString("from").ifBlank { obj.optString("sender_name") }.ifBlank { obj.optString("sender") }.ifBlank { "未知发送者" }
        val rawDate = obj.optString("date").ifBlank { obj.optString("sent_at") }.ifBlank { obj.optString("timestamp") }.ifBlank { obj.optString("date_unixtime") }
        val sourceId = obj.optString("id").ifBlank { obj.optString("message_id") }.ifBlank { sha256("$chat|$sender|$rawDate|$body") }
        return ArchiveMessage(sourceId = sourceId, chatName = chat, sender = sender, sentAt = normalizeDate(rawDate), body = body)
    }

    private fun textOf(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> (0 until value.length()).joinToString("") { index ->
            when (val item = value.opt(index)) { is String -> item; is JSONObject -> item.optString("text"); else -> "" }
        }
        is JSONObject -> value.optString("text")
        else -> ""
    }

    private fun normalizeDate(raw: String): String {
        if (raw.isBlank()) return "1970-01-01T00:00:00Z"
        raw.toLongOrNull()?.let { return Instant.ofEpochSecond(it).toString() }
        return try { Instant.parse(raw).toString() } catch (_: Exception) { raw.replace(" ", "T").let { if (it.endsWith("Z")) it else "${it}Z" } }
    }
    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun demoMessages() = listOf(
        ArchiveMessage(sourceId = "demo-1", chatName = "演示会话 A", sender = "示例用户", sentAt = "2026-01-01T09:30:00Z", body = "这是匿名演示内容，用于验证关键词检索。"),
        ArchiveMessage(sourceId = "demo-2", chatName = "演示会话 A", sender = "示例用户", sentAt = "2026-01-01T10:15:00Z", body = "导入功能仅处理用户主动选择的本地 JSON 或 JSONL 文件。"),
        ArchiveMessage(sourceId = "demo-3", chatName = "演示会话 B", sender = "示例用户", sentAt = "2026-01-02T03:20:00Z", body = "索引、收藏和消息上下文均保存在应用私有目录。"),
        ArchiveMessage(sourceId = "demo-4", chatName = "演示会话 B", sender = "示例用户", sentAt = "2026-01-02T04:50:00Z", body = "请勿将聊天导出、验证码或开发者参数提交到公开仓库。"),
        ArchiveMessage(sourceId = "demo-5", chatName = "演示会话 C", sender = "示例用户", sentAt = "2026-01-03T02:00:00Z", body = "可以使用收藏标记保存本机已导入的重点内容。"),
        ArchiveMessage(sourceId = "demo-6", chatName = "演示会话 C", sender = "示例用户", sentAt = "2026-01-03T06:10:00Z", body = "这是静态测试数据，不来自任何 Telegram 账户。")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSearchLocalApp(vm: ArchiveViewModel = viewModel()) {
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice = vm.notice
    LaunchedEffect(notice) { notice?.let { snackbars.showSnackbar(it); vm.notice = null } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importUri) }
    val palette = MaterialTheme.colorScheme

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Color(0xFF146DCE), secondary = Color(0xFF2C7A7B), tertiary = Color(0xFF7654A6))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = { CenterAlignedTopAppBar(title = { Text(if (vm.selectedMessage == null) "Telegram Search Local" else "消息详情", fontWeight = FontWeight.SemiBold) }, navigationIcon = { if (vm.selectedMessage != null) TextButton(onClick = vm::closeMessage) { Text("返回") } }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
                bottomBar = { if (vm.selectedMessage == null) AppNavigation(vm.destination) { vm.destination = it } },
                snackbarHost = { SnackbarHost(snackbars) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (vm.selectedMessage != null) MessageDetail(vm.selectedMessage!!, vm.contextMessages, vm::toggleBookmark)
                    else when (vm.destination) {
                        Destination.SEARCH -> SearchScreen(vm)
                        Destination.IMPORT -> ImportScreen(vm, onPick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        Destination.SAVED -> SavedScreen(vm)
                        Destination.SETTINGS -> SettingsScreen(vm, onClear = { scope.launch { vm.clearAll() } })
                    }
                    if (vm.loading) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(selected: Destination, navigate: (Destination) -> Unit) = NavigationBar {
    listOf(Destination.SEARCH, Destination.IMPORT, Destination.SAVED, Destination.SETTINGS).forEach { destination ->
        val icon = when (destination) { Destination.SEARCH -> Icons.Default.Search; Destination.IMPORT -> Icons.Default.FolderOpen; Destination.SAVED -> Icons.Default.Bookmark; Destination.SETTINGS -> Icons.Default.Settings }
        NavigationBarItem(selected = selected == destination, onClick = { navigate(destination) }, icon = { Icon(icon, destination.title) }, label = { Text(destination.title) })
    }
}

@Composable
private fun SearchScreen(vm: ArchiveViewModel) {
    LaunchedEffect(vm.query, vm.selectedChat, vm.fromDate, vm.untilDate) { vm.search() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("离线检索你的导出记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp)); Text("所有数据仅保存在本机；不连接 Telegram，也不上传聊天内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(value = vm.query, onValueChange = { vm.query = it }, label = { Text("输入关键词") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            LazyColumn(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth().height(if (vm.chats.size > 5) 86.dp else 44.dp), contentPadding = PaddingValues(end = 8.dp)) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = vm.selectedChat == null, onClick = { vm.selectedChat = null }, label = { Text("全部会话") }); vm.chats.take(8).forEach { chat -> FilterChip(selected = vm.selectedChat == chat, onClick = { vm.selectedChat = if (vm.selectedChat == chat) null else chat }, label = { Text(chat, maxLines = 1, overflow = TextOverflow.Ellipsis) }) } } }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = vm.fromDate, onValueChange = { vm.fromDate = it }, label = { Text("起始日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f)); OutlinedTextField(value = vm.untilDate, onValueChange = { vm.untilDate = it }, label = { Text("结束日期") }, singleLine = true, modifier = Modifier.weight(1f)) } }
        item { Text("${vm.results.size} 条结果${if (vm.results.size == 250) "（仅显示前 250 条）" else ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (vm.results.isEmpty()) item { EmptyState(Icons.Default.Search, "尚无匹配消息", "请调整关键词或从“导入”页选择聊天记录文件。") }
        items(vm.results, key = { it.localId }) { MessageCard(it, vm.query, onClick = { vm.showMessage(it) }, onBookmark = { vm.toggleBookmark(it) }) }
    }
}

@Composable
private fun ImportScreen(vm: ArchiveViewModel, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("导入聊天记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FileOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)); Text("选择本地导出文件", style = MaterialTheme.typography.titleLarge)
            Text("支持 Telegram Desktop 的 result.json、常见单聊天 JSON，以及 tg-search CLI 的逐行 JSONL 文本导出。仅解析文字消息、发送者、会话名称和时间。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("选择 JSON / JSONL 文件") }
        } }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(10.dp)); Text("不会请求 Telegram 账号、通讯录、文件或网络权限。选择文件后，索引仅留在此应用的私有存储中。") } }
        OutlinedButton(onClick = vm::addDemo, modifier = Modifier.fillMaxWidth()) { Text("载入示例数据，体验检索") }
    }
}

@Composable
private fun SavedScreen(vm: ArchiveViewModel) {
    LaunchedEffect(vm.destination) { vm.saved = ArchiveDb(vm.getApplication()).bookmarks() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("收藏消息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("保留重要结论，快速回到关键上下文。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (vm.saved.isEmpty()) item { EmptyState(Icons.Default.BookmarkBorder, "尚未收藏消息", "在搜索结果或消息详情中点击书签图标即可收藏。") }
        items(vm.saved, key = { it.localId }) { MessageCard(it, "", onClick = { vm.showMessage(it) }, onBookmark = { vm.toggleBookmark(it) }) }
    }
}

@Composable
private fun SettingsScreen(vm: ArchiveViewModel, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("本地数据与隐私", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("索引概览", style = MaterialTheme.typography.titleMedium); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Stat("消息", vm.stats.messages); Stat("会话", vm.stats.chats); Stat("收藏", vm.stats.saved) } } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("离线优先", style = MaterialTheme.typography.titleMedium); Text("本版本未声明 INTERNET 权限，也未包含 Telegram 登录或后台同步逻辑。删除索引会移除应用本机保存的消息与收藏。", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("清除所有本地索引") }
    }
}

@Composable private fun Stat(label: String, value: Int) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun MessageDetail(message: ArchiveMessage, context: List<ArchiveMessage>, onBookmark: (ArchiveMessage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(message.chatName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${message.sender} · ${displayDate(message.sentAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("原消息", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); IconButton(onClick = { onBookmark(message) }) { Icon(if (message.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "切换收藏") } }; Text(message.body, style = MaterialTheme.typography.bodyLarge) } } }
        item { Text("前后上下文", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (context.isEmpty()) item { Text("未找到相邻的已导入消息。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(context, key = { it.localId }) { item -> Card(colors = CardDefaults.cardColors(containerColor = if (item.localId == message.localId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(14.dp)) { Text("${item.sender} · ${displayDate(item.sentAt)}", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp)); Text(item.body) } } }
    }
}

@Composable
private fun MessageCard(message: ArchiveMessage, query: String, onClick: () -> Unit, onBookmark: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(message.chatName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); IconButton(onClick = onBookmark) { Icon(if (message.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "切换收藏") } }; Text("${message.sender} · ${displayDate(message.sentAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text(highlight(message.body, query), maxLines = 4, overflow = TextOverflow.Ellipsis) }
}

@Composable private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }

private fun highlight(text: String, term: String): AnnotatedString = buildAnnotatedString { if (term.isBlank()) { append(text); return@buildAnnotatedString }; val lower = text.lowercase(Locale.ROOT); val needle = term.lowercase(Locale.ROOT); var start = 0; while (true) { val at = lower.indexOf(needle, start); if (at < 0) { append(text.substring(start)); break }; append(text.substring(start, at)); pushStyle(SpanStyle(background = Color(0xFFFFE082), fontWeight = FontWeight.Bold)); append(text.substring(at, at + term.length)); pop(); start = at + term.length } }
private fun displayDate(raw: String): String = try { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(raw)) } catch (_: Exception) { raw.replace("T", " ").removeSuffix("Z") }
