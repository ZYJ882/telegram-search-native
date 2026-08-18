package app.lingogram.tgsearchnative

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale

data class ApiConfig(val apiId: Int, val apiHash: String)
data class LocalMessage(val id: Long = 0, val remoteId: Long, val chatId: Long, val chatName: String, val sender: String, val date: String, val text: String, val saved: Boolean = false)
data class LocalStats(val messages: Int, val chats: Int, val saved: Int)
data class IndexedChat(val chatId: Long, val chatName: String, val messages: Int, val saved: Int)

class SecureSettings(context: Context) {
    private val masterAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create("tg_native_secure", masterAlias, context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun apiConfig(): ApiConfig? { val id = prefs.getInt("api_id", 0); val hash = prefs.getString("api_hash", "") ?: ""; return if (id > 0 && hash.isNotBlank()) ApiConfig(id, hash) else null }
    fun saveApiConfig(id: Int, hash: String) { prefs.edit().putInt("api_id", id).putString("api_hash", hash.trim()).apply() }
    fun dbKey(): ByteArray { val old = prefs.getString("db_key", null); if (old != null) return Base64.getDecoder().decode(old); val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }; prefs.edit().putString("db_key", Base64.getEncoder().encodeToString(raw)).apply(); return raw }
    fun clear() { prefs.edit().clear().apply() }
}

class ArchiveStore(context: Context) : SQLiteOpenHelper(context, "tg_native_index.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,remote_id INTEGER NOT NULL,chat_id INTEGER NOT NULL,chat_name TEXT NOT NULL,sender TEXT NOT NULL,date TEXT NOT NULL,text TEXT NOT NULL,normalized TEXT NOT NULL,saved INTEGER NOT NULL DEFAULT 0, UNIQUE(remote_id,chat_id) ON CONFLICT IGNORE)"); db.execSQL("CREATE INDEX message_search ON messages(normalized)"); db.execSQL("CREATE INDEX message_chat_date ON messages(chat_id,date)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun insert(items: List<LocalMessage>): Int { var count = 0; writableDatabase.beginTransaction(); try { items.forEach { m -> val s = writableDatabase.compileStatement("INSERT OR IGNORE INTO messages(remote_id,chat_id,chat_name,sender,date,text,normalized) VALUES(?,?,?,?,?,?,?)"); s.bindLong(1,m.remoteId);s.bindLong(2,m.chatId);s.bindString(3,m.chatName);s.bindString(4,m.sender);s.bindString(5,m.date);s.bindString(6,m.text);s.bindString(7,m.text.lowercase(Locale.ROOT));if(s.executeInsert()!=-1L)count++;s.close() }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }; return count }
    private fun searchTerms(query: String): List<String> = query.lowercase(Locale.ROOT).split(Regex("[\\s,，、;；|/]+")).filter { it.isNotBlank() }.distinct()
    fun search(q: String, chat: Long? = null): List<LocalMessage> { val where=mutableListOf<String>(); val args=mutableListOf<String>(); searchTerms(q).forEach{term->where+="normalized LIKE ?";args+="%$term%"};if(chat!=null){where+="chat_id=?";args+=chat.toString()}; return readableDatabase.query("messages",null,where.joinToString(" AND ").ifBlank{null},args.toTypedArray(),null,null,"date DESC").use{c->rows(c)} }
    fun saved(): List<LocalMessage> = readableDatabase.query("messages",null,"saved=1",null,null,null,"date DESC").use{rows(it)}
    fun toggle(id:Long) { val before=readableDatabase.rawQuery("SELECT saved FROM messages WHERE id=?",arrayOf(id.toString())).use{it.moveToFirst()&&it.getInt(0)==1}; writableDatabase.execSQL("UPDATE messages SET saved=? WHERE id=?",arrayOf(if(before)0 else 1,id)) }
    fun stats():LocalStats=readableDatabase.rawQuery("SELECT count(*),count(DISTINCT chat_id),sum(saved) FROM messages",null).use{it.moveToFirst();LocalStats(it.getInt(0),it.getInt(1),if(it.isNull(2))0 else it.getInt(2))}
    fun indexedChats(): List<IndexedChat> = readableDatabase.rawQuery("SELECT chat_id, chat_name, count(*), sum(saved) FROM messages GROUP BY chat_id, chat_name ORDER BY chat_name COLLATE NOCASE", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(IndexedChat(cursor.getLong(0), cursor.getString(1), cursor.getInt(2), if (cursor.isNull(3)) 0 else cursor.getInt(3))) }
    }
    fun deleteMessage(id: Long) { writableDatabase.delete("messages", "id=?", arrayOf(id.toString())) }
    fun deleteChatIndex(chatId: Long): Int = writableDatabase.delete("messages", "chat_id=?", arrayOf(chatId.toString()))
    fun clearIndex(): Int = writableDatabase.delete("messages",null,null)
    fun addDemo():Int = insert(listOf(LocalMessage(remoteId=1,chatId=100,chatName="产品讨论",sender="林晓",date="2026-08-12T09:30:00Z",text="TDLib 登录后，搜索页只检索手机本地同步并建立索引的消息。"),LocalMessage(remoteId=2,chatId=100,chatName="产品讨论",sender="你",date="2026-08-12T10:00:00Z",text="验证码和两步验证密码不会写入应用数据库或日志。"),LocalMessage(remoteId=3,chatId=200,chatName="项目进度",sender="陈峰",date="2026-08-13T10:00:00Z",text="请先选择会话，再同步最近消息到离线检索索引。")))
    private fun rows(c:Cursor):List<LocalMessage>{ val r=mutableListOf<LocalMessage>();while(c.moveToNext())r+=LocalMessage(c.getLong(c.getColumnIndexOrThrow("id")),c.getLong(c.getColumnIndexOrThrow("remote_id")),c.getLong(c.getColumnIndexOrThrow("chat_id")),c.getString(c.getColumnIndexOrThrow("chat_name")),c.getString(c.getColumnIndexOrThrow("sender")),c.getString(c.getColumnIndexOrThrow("date")),c.getString(c.getColumnIndexOrThrow("text")),c.getInt(c.getColumnIndexOrThrow("saved"))==1);return r }
}

fun epochText(seconds:Int):String=try{Instant.ofEpochSecond(seconds.toLong()).toString()}catch(_:Exception){"1970-01-01T00:00:00Z"}
