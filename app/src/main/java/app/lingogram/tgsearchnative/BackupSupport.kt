package app.lingogram.tgsearchnative

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BACKUP_FORMAT = "telegram-search-native-backup"
private const val BACKUP_SCHEMA = 1

class BackupFormatException(message: String) : Exception(message)

data class BackupPayload(
    val messages: List<LocalMessage>,
    val customApi: ApiConfig?
)

object LocalBackup {
    fun write(output: OutputStream, messages: List<LocalMessage>, customApi: ApiConfig?, appVersion: String) {
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "manifest.json") {
                JSONObject()
                    .put("format", BACKUP_FORMAT)
                    .put("schema", BACKUP_SCHEMA)
                    .put("exportedAt", Instant.now().toString())
                    .put("appVersion", appVersion)
                    .toString()
            }
            writeEntry(zip, "settings.json") {
                JSONObject()
                    .put("hasCustomApi", customApi != null)
                    .apply {
                        customApi?.let {
                            put("apiId", it.apiId)
                            put("apiHash", it.apiHash)
                        }
                    }
                    .toString()
            }
            zip.putNextEntry(ZipEntry("messages.jsonl"))
            OutputStreamWriter(zip, StandardCharsets.UTF_8).also { writer ->
                messages.forEach { message ->
                    writer.write(
                        JSONObject()
                            .put("id", message.id)
                            .put("remoteId", message.remoteId)
                            .put("chatId", message.chatId)
                            .put("chatName", message.chatName)
                            .put("sender", message.sender)
                            .put("date", message.date)
                            .put("text", message.text)
                            .put("saved", message.saved)
                            .toString()
                    )
                    writer.write("\n")
                }
                writer.flush()
            }
            zip.closeEntry()
        }
    }

    fun read(input: InputStream): BackupPayload {
        var manifest: JSONObject? = null
        var settings: JSONObject? = null
        val messages = mutableListOf<LocalMessage>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "manifest.json" -> manifest = JSONObject(readText(zip))
                    "settings.json" -> settings = JSONObject(readText(zip))
                    "messages.jsonl" -> readMessages(zip, messages)
                }
                zip.closeEntry()
            }
        }
        val metadata = manifest ?: throw BackupFormatException("备份文件缺少格式信息")
        if (metadata.optString("format") != BACKUP_FORMAT || metadata.optInt("schema", -1) != BACKUP_SCHEMA) {
            throw BackupFormatException("不支持的备份格式或版本")
        }
        if (settings == null) throw BackupFormatException("备份文件缺少设置数据")
        val setting = settings!!
        val customApi = if (setting.optBoolean("hasCustomApi", false)) {
            val id = setting.optInt("apiId", 0)
            val hash = setting.optString("apiHash", "").trim()
            if (id <= 0 || hash.isBlank()) throw BackupFormatException("备份中的 API 配置无效")
            ApiConfig(id, hash)
        } else null
        return BackupPayload(messages, customApi)
    }

    private fun readMessages(input: InputStream, target: MutableList<LocalMessage>) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) {
                try {
                    val item = JSONObject(line)
                    target += LocalMessage(
                        id = item.optLong("id", 0),
                        remoteId = item.getLong("remoteId"),
                        chatId = item.getLong("chatId"),
                        chatName = item.getString("chatName"),
                        sender = item.getString("sender"),
                        date = item.getString("date"),
                        text = item.getString("text"),
                        saved = item.optBoolean("saved", false)
                    )
                } catch (error: Exception) {
                    throw BackupFormatException("备份中的消息数据损坏：${error.message ?: "格式错误"}")
                }
            }
        }
    }

    private fun readText(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val result = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: () -> String) {
        zip.putNextEntry(ZipEntry(name))
        OutputStreamWriter(zip, StandardCharsets.UTF_8).also {
            it.write(content())
            it.flush()
        }
        zip.closeEntry()
    }
}

fun Context.writeBackup(uri: Uri, messages: List<LocalMessage>, customApi: ApiConfig?) {
    contentResolver.openOutputStream(uri)?.use { output ->
        LocalBackup.write(output, messages, customApi, BuildConfig.VERSION_NAME)
    } ?: throw BackupFormatException("无法写入备份文件")
}

fun Context.readBackup(uri: Uri): BackupPayload {
    return contentResolver.openInputStream(uri)?.use(LocalBackup::read)
        ?: throw BackupFormatException("无法读取备份文件")
}
