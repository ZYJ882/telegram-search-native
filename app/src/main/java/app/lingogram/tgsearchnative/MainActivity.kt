package app.lingogram.tgsearchnative

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.roundToInt

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
    var indexedChats by mutableStateOf(emptyList<IndexedChat>())
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
        indexedChats = store.indexedChats()
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

    fun setSaved(ids: Collection<Long>, saved: Boolean) {
        val changed = store.setSaved(ids, saved)
        refresh()
        notice = if (saved) "已将 $changed 条消息加入本机收藏" else "已将 $changed 条消息移出本机收藏"
    }

    fun deleteLocalMessages(ids: Collection<Long>) {
        val deleted = store.deleteMessages(ids)
        refresh()
        if (selected?.id in ids) closeDetail()
        notice = "已从本机索引删除 $deleted 条消息"
    }

    fun demo() {
        store.addDemo()
        refresh()
        notice = "已加载本地演示数据"
    }

    fun deleteChatIndex(chat: IndexedChat) {
        val deleted = store.deleteChatIndex(chat.chatId)
        refresh()
        if (selected?.chatId == chat.chatId) closeDetail()
        notice = "已删除「${chat.chatName}」的 $deleted 条本机索引"
    }

    fun clearIndex() {
        val deleted = store.clearIndex()
        refresh()
        selected = null
        notice = "已删除 $deleted 条本机消息、收藏和搜索索引"
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

@Composable
fun Search(vm: AppViewModel) {
    val listState = rememberLazyListState(vm.searchListIndex, vm.searchListOffset)
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulkDelete by rememberSaveable { mutableStateOf(false) }
    val allResultIds = remember(vm.results) { vm.results.map { it.id }.toSet() }

    LaunchedEffect(vm.query) {
        vm.refresh()
        selectionMode = false
        selectedIds = emptySet()
    }
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }
    val timeOrderedMessages = remember(vm.results) {
        vm.results.sortedByDescending { it.date }
    }
    val messageItemStartIndex = 3
    val totalListItems = timeOrderedMessages.size
    val lastSearchListIndex = messageItemStartIndex + totalListItems - 1
    var requestedListIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(requestedListIndex) {
        requestedListIndex?.let { listState.scrollToItem(it) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (timeOrderedMessages.isEmpty()) 0.dp else 26.dp),
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
                Text("${vm.results.size} 条结果 · ${vm.stats.saved} 条本机收藏 · 按时间排序，可通过右侧滑块快速定位", color = MaterialTheme.colorScheme.primary)
            }
            if (vm.results.isEmpty()) {
                item { Empty("尚无本地消息", "先连接 Telegram 并选择会话同步，或载入演示数据。") }
            }
            items(timeOrderedMessages, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    open = {
                        vm.open(
                            message,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset
                        )
                    },
                    toggle = { vm.toggle(message) },
                    selectionMode = selectionMode,
                    selected = message.id in selectedIds,
                    onToggleSelection = {
                        selectedIds = if (message.id in selectedIds) selectedIds - message.id else selectedIds + message.id
                    },
                    onLongPress = {
                        selectionMode = true
                        selectedIds = selectedIds + message.id
                    }
                )
            }
        }

        if (timeOrderedMessages.isNotEmpty() && !selectionMode) {
            SearchQuickSlider(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 24.dp, bottom = 24.dp),
                listState = listState,
                firstSearchItemIndex = messageItemStartIndex,
                lastSearchItemIndex = lastSearchListIndex,
                onSeekTo = { requestedListIndex = it }
            )
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选 ${selectedIds.size}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.containsAll(allResultIds) && allResultIds.isNotEmpty()) emptySet() else allResultIds
                        }
                    ) { Text(if (selectedIds.containsAll(allResultIds) && allResultIds.isNotEmpty()) "取消全选" else "全选") }
                    IconButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            vm.setSaved(selectedIds, true)
                            selectionMode = false
                            selectedIds = emptySet()
                        }
                    ) { Icon(Icons.Default.Bookmark, "加入本机收藏") }
                    IconButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { confirmBulkDelete = true }
                    ) { Icon(Icons.Default.Delete, "删除本机索引") }
                    IconButton(onClick = {
                        selectionMode = false
                        selectedIds = emptySet()
                    }) { Icon(Icons.Default.Close, "退出多选") }
                }
            }
        }
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除已选索引？") },
            text = { Text("这会从当前设备的本机索引中删除已选 ${selectedIds.size} 条消息；Telegram 账号中的原始消息不会受到影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteLocalMessages(selectedIds)
                        confirmBulkDelete = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SearchQuickSlider(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstSearchItemIndex: Int,
    lastSearchItemIndex: Int,
    onSeekTo: (Int) -> Unit
) {
    var sliderHeightPx by remember { mutableIntStateOf(0) }
    var touchFraction by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    var lastTargetIndex by remember { mutableIntStateOf(-1) }
    val visibleFraction by remember(listState, firstSearchItemIndex, lastSearchItemIndex) {
        derivedStateOf {
            val itemRange = (lastSearchItemIndex - firstSearchItemIndex).coerceAtLeast(1)
            ((listState.firstVisibleItemIndex - firstSearchItemIndex).coerceAtLeast(0).toFloat() /
                itemRange.toFloat()).coerceIn(0f, 1f)
        }
    }
    val displayFraction = if (isTouching) touchFraction else visibleFraction
    val displayPercent = (displayFraction * 100).roundToInt()
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val knobColor = MaterialTheme.colorScheme.primary
    val knobCenterColor = MaterialTheme.colorScheme.surface

    fun updateFromTouch(y: Float) {
        if (sliderHeightPx <= 0) return
        val fraction = (y / sliderHeightPx.toFloat()).coerceIn(0f, 1f)
        touchFraction = fraction
        val itemRange = (lastSearchItemIndex - firstSearchItemIndex).coerceAtLeast(1)
        val target = firstSearchItemIndex + (fraction * itemRange).roundToInt()
        if (target != lastTargetIndex) {
            lastTargetIndex = target
            onSeekTo(target)
        }
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(330.dp)
    ) {
        if (isTouching) {
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
                    Text("$displayPercent%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(48.dp)
                .fillMaxHeight()
                .onSizeChanged { sliderHeightPx = it.height }
                .pointerInput(sliderHeightPx, lastSearchItemIndex) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.pressed) {
                                isTouching = true
                                updateFromTouch(change.position.y)
                                change.consume()
                            } else {
                                isTouching = false
                                lastTargetIndex = -1
                            }
                        }
                    }
                }
        ) {
            val trackX = size.width * 0.72f
            val top = 12.dp.toPx()
            val bottom = size.height - 12.dp.toPx()
            val knobY = top + (bottom - top) * displayFraction
            drawLine(
                color = trackColor,
                start = Offset(trackX, top),
                end = Offset(trackX, bottom),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = knobColor,
                radius = if (isTouching) 12.dp.toPx() else 9.dp.toPx(),
                center = Offset(trackX, knobY)
            )
            drawCircle(
                color = knobCenterColor,
                radius = 3.dp.toPx(),
                center = Offset(trackX, knobY)
            )
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
    var showIndexManager by rememberSaveable { mutableStateOf(false) }
    var pendingChatDelete by remember { mutableStateOf<IndexedChat?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
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
                Text("搜索索引管理", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${vm.stats.messages} 条已索引消息 · ${vm.stats.chats} 个会话 · ${vm.stats.saved} 条本机收藏",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "删除索引只移除当前手机中本应用保存的消息和本机收藏，不会删除 Telegram 账号中的聊天、消息或收藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { if (vm.stats.messages > 0) confirmClear = true else vm.notice = "暂无搜索索引可删除" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除全部搜索索引")
                }
                TextButton(
                    onClick = { showIndexManager = !showIndexManager },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (showIndexManager) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showIndexManager) "收起会话索引" else "按会话删除索引（${vm.indexedChats.size}）")
                }
                if (showIndexManager) {
                    HorizontalDivider()
                    if (vm.indexedChats.isEmpty()) {
                        Text("尚无可管理的本机索引。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        vm.indexedChats.forEach { chat ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(chat.chatName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${chat.messages} 条已索引${if (chat.saved > 0) " · ${chat.saved} 条本机收藏" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingChatDelete = chat }) {
                                    Icon(Icons.Default.Delete, "删除此会话索引", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::demo, modifier = Modifier.fillMaxWidth()) { Text("载入演示数据") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除全部搜索索引？") },
            text = { Text("这会删除当前设备中的全部同步消息、搜索索引和本机收藏。Telegram 账号中的聊天记录和收藏不会受到影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearIndex()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }

    pendingChatDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingChatDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除「${chat.chatName}」的索引？") },
            text = { Text("这会删除该会话在当前设备中保存的 ${chat.messages} 条索引消息${if (chat.saved > 0) "和 ${chat.saved} 条本机收藏" else ""}。Telegram 中的原始消息不会受到影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteChatIndex(chat)
                        pendingChatDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingChatDelete = null }) { Text("取消") } }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageCard(
    message: LocalMessage,
    open: () -> Unit,
    toggle: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelection() else open() },
                onLongClick = onLongPress
            ),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection() }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    message.chatName,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(onClick = toggle) {
                        Icon(if (message.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                    }
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
