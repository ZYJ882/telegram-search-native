package app.lingogram.tgsearchnative

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

enum class Tab(val label: String) {
    SEARCH("搜索"),
    SAVED("收藏"),
    SYNC("同步"),
    CONNECT("连接"),
    SETTINGS("设置")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val secure = SecureSettings(app)
    private val store = ArchiveStore(app)

    val gateway = TdLibGateway(
        app,
        secure,
        { items, afterInsert ->
            viewModelScope.launch {
                val inserted = withContext(Dispatchers.IO) { store.insert(items) }
                refresh()
                afterInsert(inserted)
                if (inserted > 0) notice = "本页新增 $inserted 条本地索引"
            }
        },
        { message -> notice = message }
    )

    var tab by mutableStateOf(Tab.SEARCH)
    var query by mutableStateOf("")
    var results by mutableStateOf(emptyList<LocalMessage>())
    var saved by mutableStateOf(emptyList<LocalMessage>())
    var stats by mutableStateOf(LocalStats(0, 0, 0))
    var notice by mutableStateOf<String?>(null)
    var config by mutableStateOf(secure.apiConfig())
    var selected by mutableStateOf<LocalMessage?>(null)
    var searchListIndex by mutableIntStateOf(0)
    var searchListOffset by mutableIntStateOf(0)

    init {
        refresh()
        gateway.start()
    }

    fun refresh() {
        results = store.search(query)
        saved = store.saved()
        stats = store.stats()
    }

    fun saveConfig(idText: String, hash: String) {
        val id = idText.toIntOrNull()
        if (id == null || hash.isBlank()) {
            notice = "请输入有效 API ID 与 API Hash"
            return
        }
        secure.saveApiConfig(id, hash)
        config = secure.apiConfig()
        gateway.restart()
        notice = "参数已加密保存到当前设备"
    }

    fun toggle(message: LocalMessage) {
        store.toggle(message.id)
        refresh()
        selected = results.firstOrNull { it.id == message.id }
            ?: saved.firstOrNull { it.id == message.id }
            ?: message.copy(saved = !message.saved)
        notice = if (message.saved) "已从本机收藏移除" else "已加入本机收藏"
    }

    fun open(message: LocalMessage, index: Int, offset: Int) {
        searchListIndex = index
        searchListOffset = offset
        selected = message
    }

    fun closeDetail() {
        selected = null
    }

    fun copyMessage(message: LocalMessage) {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram message", message.text))
        notice = "已复制全文到系统剪贴板"
    }

    fun deleteLocal(message: LocalMessage) {
        store.deleteMessage(message.id)
        refresh()
        if (selected?.id == message.id) closeDetail()
        notice = "已从本机索引删除该消息"
    }

    fun demo() {
        store.addDemo()
        refresh()
        notice = "已加载本地演示数据"
    }

    fun clearIndex() {
        store.clearIndex()
        refresh()
        selected = null
        notice = "本地消息、收藏和搜索索引已清除"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel = viewModel()) {
    BackHandler(enabled = vm.selected != null) { vm.closeDetail() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            snackbar.showSnackbar(it)
            vm.notice = null
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF146DCE),
            secondary = Color(0xFF2D7D75)
        )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            if (vm.selected == null) "Telegram Search Native" else "消息详情",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        if (vm.selected != null) {
                            TextButton(onClick = vm::closeDetail) { Text("返回") }
                        }
                    }
                )
            },
            bottomBar = {
                if (vm.selected == null) Nav(vm.tab) { vm.tab = it }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                val selected = vm.selected
                if (selected != null) {
                    Detail(selected, vm)
                } else {
                    when (vm.tab) {
                        Tab.SEARCH -> Search(vm)
                        Tab.SAVED -> SavedMessages(vm)
                        Tab.SYNC -> Sync(vm)
                        Tab.CONNECT -> Connect(vm)
                        Tab.SETTINGS -> Settings(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun Nav(selected: Tab, go: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            val icon = when (tab) {
                Tab.SEARCH -> Icons.Default.Search
                Tab.SAVED -> Icons.Default.Bookmark
                Tab.SYNC -> Icons.Default.Sync
                Tab.CONNECT -> Icons.Default.Lock
                Tab.SETTINGS -> Icons.Default.Settings
            }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { go(tab) },
                icon = { Icon(icon, tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}

private data class SearchSection(val key: String, val messages: List<LocalMessage>)
private val searchIndexKeys = listOf("#") + ('A'..'Z').map { it.toString() }

private fun searchIndexKey(chatName: String): String {
    val first = chatName.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}

@Composable
fun Search(vm: AppViewModel) {
    LaunchedEffect(vm.query) { vm.refresh() }
    val listState = rememberLazyListState(vm.searchListIndex, vm.searchListOffset)
    val sections = remember(vm.results) {
        val messagesByKey = vm.results.groupBy { searchIndexKey(it.chatName) }
        searchIndexKeys.mapNotNull { key ->
            messagesByKey[key]?.let { messages ->
                SearchSection(
                    key,
                    messages.sortedWith(
                        compareBy<LocalMessage> { it.chatName.lowercase(Locale.ROOT) }
                            .thenByDescending { it.date }
                    )
                )
            }
        }
    }
    val sectionStartIndices = remember(sections) {
        buildMap {
            var index = 3
            sections.forEach { section ->
                put(section.key, index)
                index += 1 + section.messages.size
            }
        }
    }
    val activeIndexKey by remember(listState, sectionStartIndices, sections) {
        derivedStateOf {
            sectionStartIndices.entries
                .filter { it.value <= listState.firstVisibleItemIndex }
                .maxByOrNull { it.value }
                ?.key
                ?: sections.firstOrNull()?.key
        }
    }
    var requestedIndexKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedIndexKey, sectionStartIndices) {
        requestedIndexKey?.let { key ->
            sectionStartIndices[key]?.let { listState.scrollToItem(it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (sections.isEmpty()) 0.dp else 26.dp),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("手机本地检索", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("多个关键词可用空格、逗号、顿号、分号、竖线或斜杠分隔；结果须同时包含全部关键词。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("关键词（空格或逗号分隔）") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
            }
            item {
                Text("${vm.results.size} 条结果 · ${vm.stats.saved} 条本机收藏 · 按会话名称 #–Z 分组", color = MaterialTheme.colorScheme.primary)
            }
            if (vm.results.isEmpty()) {
                item { Empty("尚无本地消息", "先连接 Telegram 并选择会话同步，或载入演示数据。") }
            }
            sections.forEach { section ->
                item(key = "section-${section.key}") {
                    Text(
                        section.key,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(section.messages, key = { it.id }) { message ->
                    MessageCard(
                        message,
                        {
                            vm.open(
                                message,
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset
                            )
                        },
                        { vm.toggle(message) }
                    )
                }
            }
        }

        if (sections.isNotEmpty()) {
            SearchIndexRail(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                availableKeys = sectionStartIndices.keys,
                activeKey = activeIndexKey,
                onSelect = { requestedIndexKey = it }
            )
        }
    }
}

@Composable
private fun SearchIndexRail(
    modifier: Modifier = Modifier,
    availableKeys: Set<String>,
    activeKey: String?,
    onSelect: (String) -> Unit
) {
    var railHeightPx by remember { mutableIntStateOf(0) }
    var lastTouchedKey by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    val availableIndices = remember(availableKeys) {
        searchIndexKeys.indices.filter { searchIndexKeys[it] in availableKeys }
    }

    fun nearestAvailableKey(y: Float): String? {
        if (railHeightPx <= 0 || availableIndices.isEmpty()) return null
        val rawIndex = (y / railHeightPx * searchIndexKeys.size)
            .toInt()
            .coerceIn(0, searchIndexKeys.lastIndex)
        return availableIndices.minByOrNull { abs(it - rawIndex) }?.let { searchIndexKeys[it] }
    }

    fun selectAt(y: Float) {
        nearestAvailableKey(y)?.let { key ->
            if (key != lastTouchedKey) {
                lastTouchedKey = key
                onSelect(key)
            }
        }
    }

    Box(
        modifier = modifier
            .width(72.dp)
            .height(370.dp)
    ) {
        if (isDragging && lastTouchedKey != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(52.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(lastTouchedKey!!, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(22.dp)
                .fillMaxHeight()
                .onSizeChanged { railHeightPx = it.height }
                .pointerInput(availableKeys, railHeightPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed) {
                                isDragging = true
                                selectAt(change.position.y)
                                change.consume()
                            } else {
                                isDragging = false
                                lastTouchedKey = null
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            searchIndexKeys.forEach { key ->
                val available = key in availableKeys
                Text(
                    text = key,
                    fontSize = 8.sp,
                    fontWeight = if (key == activeKey) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        key == activeKey -> MaterialTheme.colorScheme.primary
                        available -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                    }
                )
            }
        }
    }
}

@Composable
fun SavedMessages(vm: AppViewModel) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("本机收藏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "收藏仅保存在当前设备的本地索引中，不会同步到 Telegram 收藏夹。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text("${vm.saved.size} 条本机收藏", color = MaterialTheme.colorScheme.primary)
        }
        if (vm.saved.isEmpty()) {
            item { Empty("暂无本机收藏", "在搜索结果或消息详情点击书签图标即可加入收藏。") }
        }
        items(vm.saved, key = { it.id }) { message ->
            MessageCard(
                message,
                {
                    vm.open(
                        message,
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset
                    )
                },
                { vm.toggle(message) }
            )
        }
    }
}

@Composable
fun Connect(vm: AppViewModel) {
    val stage = vm.gateway.stage
    var input by remember(stage, vm.gateway.recoveryHint) { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设备内 Telegram 登录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stage.title, style = MaterialTheme.typography.titleLarge)
                Text(stage.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.gateway.recoveryHint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (stage) {
                    AuthStage.NeedConfig -> Button(
                        onClick = { vm.tab = Tab.SETTINGS },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("去设置 API 参数") }

                    AuthStage.NeedPhone -> {
                        OutlinedTextField(
                            input,
                            { input = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("手机号（含国家码）") },
                            singleLine = true
                        )
                        Button(onClick = { vm.gateway.sendPhone(input) }, modifier = Modifier.fillMaxWidth()) {
                            Text("发送登录请求")
                        }
                    }

                    AuthStage.NeedCode -> {
                        OutlinedTextField(
                            input,
                            { input = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("Telegram 验证码") },
                            singleLine = true
                        )
                        Button(onClick = { vm.gateway.sendCode(input) }, modifier = Modifier.fillMaxWidth()) {
                            Text("验证")
                        }
                        TextButton(onClick = vm.gateway::restart, modifier = Modifier.fillMaxWidth()) {
                            Text("验证码无效或过期？重新开始登录")
                        }
                    }

                    AuthStage.NeedPassword -> {
                        OutlinedTextField(
                            input,
                            { input = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("两步验证密码") },
                            singleLine = true
                        )
                        Button(onClick = { vm.gateway.sendPassword(input) }, modifier = Modifier.fillMaxWidth()) {
                            Text("继续")
                        }
                    }

                    is AuthStage.Failed -> OutlinedButton(
                        onClick = { vm.gateway.restart() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重新开始登录") }

                    else -> Unit
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                "手机号、验证码和两步验证密码不会写入本地检索库或日志。API 参数经加密偏好保存，TDLib 会话数据库仅保存在当前手机的应用私有目录。",
                Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun Sync(vm: AppViewModel) {
    val syncing = vm.gateway.syncInProgress
    var chatQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedChats = vm.gateway.chats.filter { it.selected }.sortedBy { it.title.lowercase() }
    val matchingUnselected = vm.gateway.chats
        .filter { !it.selected && (chatQuery.isBlank() || it.title.contains(chatQuery, ignoreCase = true)) }
        .sortedBy { it.title.lowercase() }
    val visibleChats = selectedChats + matchingUnselected

    fun choose(chat: RemoteChat) {
        val willSelect = !chat.selected
        vm.gateway.toggleChat(chat.id)
        if (willSelect) scope.launch { listState.animateScrollToItem(0) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("选择会话并同步", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("按名称搜索会话；已勾选会话始终置顶。对已选会话按页拉取全部可访问的历史文本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(
                chatQuery,
                { chatQuery = it },
                Modifier.fillMaxWidth(),
                label = { Text("搜索会话名称") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (chatQuery.isNotBlank()) {
                        IconButton(onClick = { chatQuery = "" }) { Icon(Icons.Default.Close, "清除搜索") }
                    }
                },
                singleLine = true
            )
        }
        item {
            Text(
                "已选 ${selectedChats.size} 个会话${if (chatQuery.isNotBlank()) " · 搜索结果 ${matchingUnselected.size} 个未选会话" else ""}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Button(
                onClick = vm.gateway::syncSelected,
                enabled = vm.gateway.stage == AuthStage.Ready && !syncing && selectedChats.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(if (syncing) "正在同步全部历史…" else "同步已选 ${selectedChats.size} 个会话的全部历史")
            }
        }
        if (syncing) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "会话 ${vm.gateway.syncFinishedChats + 1}/${vm.gateway.syncTotalChats} · ${vm.gateway.syncCurrentChat ?: "准备中"} · 第 ${vm.gateway.syncPagesFetched} 页 · 已扫描 ${vm.gateway.syncProcessedMessages} 条，新增 ${vm.gateway.syncIndexedMessages} 条文本索引",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = vm.gateway::cancelSync, modifier = Modifier.fillMaxWidth()) {
                    Text("完成当前页后取消同步")
                }
            }
        }
        item { Text(vm.gateway.syncNote, color = MaterialTheme.colorScheme.primary) }
        if (vm.gateway.chats.isEmpty()) item { Empty("尚无会话", "连接成功后，TDLib 会加载聊天列表。") }
        if (selectedChats.isNotEmpty()) item {
            Text("已选会话（置顶）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        items(visibleChats, key = { it.id }) { chat ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !syncing) { choose(chat) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(chat.selected, { choose(chat) }, enabled = !syncing)
                Spacer(Modifier.width(10.dp))
                Text(chat.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (visibleChats.isEmpty() && vm.gateway.chats.isNotEmpty()) {
            item { Empty("未找到会话", "修改搜索词，或清除搜索框查看全部会话。") }
        }
    }
}

@Composable
fun Settings(vm: AppViewModel) {
    var id by remember(vm.config) { mutableStateOf(vm.config?.apiId?.toString() ?: "") }
    var hash by remember(vm.config) { mutableStateOf(vm.config?.apiHash ?: "") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("本地安全设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("请使用自己在 my.telegram.org/apps 创建的参数。它们会通过 Android Keystore 支撑的加密偏好保存在此设备。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), label = { Text("Telegram API ID") }, singleLine = true)
        OutlinedTextField(hash, { hash = it }, Modifier.fillMaxWidth(), label = { Text("Telegram API Hash") }, singleLine = true)
        Button(onClick = { vm.saveConfig(id, hash) }, modifier = Modifier.fillMaxWidth()) {
            Text("保存并初始化 TDLib")
        }
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本地数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${vm.stats.messages} 条消息 · ${vm.stats.chats} 个会话 · ${vm.stats.saved} 条本机收藏",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "清除后将删除本机同步消息、搜索索引和本机收藏，不影响 Telegram 账号中的聊天或收藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { if (vm.stats.messages > 0) confirmClear = true else vm.notice = "暂无本地数据可清除" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("清除本地数据")
                }
            }
        }
        OutlinedButton(onClick = vm::demo, modifier = Modifier.fillMaxWidth()) { Text("载入演示数据") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("清除本地数据？") },
            text = { Text("这会删除当前设备中的同步消息、搜索索引和本机收藏。Telegram 账号中的聊天记录和收藏不会受到影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearIndex()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}

@Composable
fun Detail(message: LocalMessage, vm: AppViewModel) {
    var confirmDelete by rememberSaveable(message.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message.chatName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${message.sender} · ${message.date}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (message.saved) "已加入本机收藏" else "已同步消息", color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { vm.toggle(message) }) {
                        Icon(if (message.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                    }
                }
                SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        OutlinedButton(onClick = { vm.copyMessage(message) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("复制全文")
        }
        Button(
            onClick = { vm.gateway.forwardToSavedMessages(message) },
            enabled = vm.gateway.stage == AuthStage.Ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("转发到 Telegram 收藏夹")
        }
        OutlinedButton(
            onClick = { vm.gateway.openMessageInTelegram(message) },
            enabled = vm.gateway.stage == AuthStage.Ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInNew, null)
            Spacer(Modifier.width(8.dp))
            Text("在 Telegram 中打开")
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("从本机索引删除")
        }
        Text(
            "可长按正文自由选择文字。本机收藏只保存在此设备；Telegram 收藏夹会向 Telegram 账号发送转发请求。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("从本机删除此消息？") },
            text = { Text("删除后，该消息将从本机搜索结果和本机收藏中移除。Telegram 中的原始消息不会受到影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteLocal(message)
                        confirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
fun MessageCard(message: LocalMessage, open: () -> Unit, toggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { open() }) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.chatName,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = toggle) {
                    Icon(if (message.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                }
            }
            Text(
                "${message.sender} · ${message.date.replace("T", " ").removeSuffix("Z")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            Text(message.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Empty(title: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
