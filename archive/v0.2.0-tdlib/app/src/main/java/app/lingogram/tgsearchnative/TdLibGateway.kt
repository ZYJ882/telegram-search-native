package app.lingogram.tgsearchnative

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

sealed class AuthStage(val title:String,val detail:String) {
    data object NeedConfig:AuthStage("配置 Telegram API","在设置页填写你自己的 API ID 与 API Hash。")
    data object Starting:AuthStage("正在初始化","正在打开设备内 TDLib 数据库。")
    data object NeedPhone:AuthStage("输入手机号","使用含国家/地区码的格式，例如 +8613800000000。")
    data object NeedCode:AuthStage("输入验证码","验证码由 Telegram 指定的方式发送。")
    data object NeedPassword:AuthStage("两步验证","此账号启用了 Telegram 额外密码。")
    data object NeedOtherDevice:AuthStage("在其他设备确认","请在已有 Telegram 设备上打开确认链接。")
    data object Ready:AuthStage("已连接","会话与索引只保存在当前手机。")
    data class Failed(val reason:String):AuthStage("连接失败",reason)
}
data class RemoteChat(val id:Long,val title:String,val selected:Boolean=false)

class TdLibGateway(private val context:Context, private val secure:SecureSettings, private val onIndex:(List<LocalMessage>)->Unit) {
    var stage by mutableStateOf<AuthStage>(if(secure.apiConfig()==null) AuthStage.NeedConfig else AuthStage.Starting); private set
    var chats by mutableStateOf(emptyList<RemoteChat>()); private set
    var syncNote by mutableStateOf("等待连接"); private set
    private var client:Client?=null
    private val main=Handler(Looper.getMainLooper())
    private val handler=Client.ResultHandler { obj -> main.post { handle(obj) } }

    fun start() { val cfg=secure.apiConfig()?:run{stage=AuthStage.NeedConfig;return}; if(client!=null)return; try { client=Client.create(handler,null,null); stage=AuthStage.Starting } catch(e:Throwable){stage=AuthStage.Failed("TDLib 原生库无法加载：${e.message ?: e.javaClass.simpleName}") } }
    fun restart(){ client?.send(TdApi.Close(),handler); client=null; chats=emptyList();start() }
    fun sendPhone(value:String){client?.send(TdApi.SetAuthenticationPhoneNumber(value.trim(),null),handler)}
    fun sendCode(value:String){client?.send(TdApi.CheckAuthenticationCode(value.trim()),handler)}
    fun sendPassword(value:String){client?.send(TdApi.CheckAuthenticationPassword(value),handler)}
    fun toggleChat(id:Long){chats=chats.map{if(it.id==id)it.copy(selected=!it.selected)else it}}
    fun syncSelected(){val selected=chats.filter{it.selected};if(selected.isEmpty()){syncNote="请至少选择一个会话";return};syncNote="正在同步 ${selected.size} 个会话的最近 100 条文本消息…";selected.forEach{chat->client?.send(TdApi.GetChatHistory(chat.id,0,0,100,false),Client.ResultHandler{obj->main.post{if(obj is TdApi.Messages){val mapped=obj.messages.mapNotNull{m->toLocal(m,chat)};onIndex(mapped);syncNote="已处理 ${chat.title}：${mapped.size} 条文本消息"}else if(obj is TdApi.Error)syncNote="同步失败：${obj.message}"}})}}
    fun logout(){client?.send(TdApi.LogOut(),handler); chats=emptyList();stage=AuthStage.NeedConfig}
    private fun handle(obj:TdApi.Object){when(obj){is TdApi.UpdateAuthorizationState->onAuth(obj.authorizationState);is TdApi.UpdateNewChat->upsert(obj.chat.id,obj.chat.title);is TdApi.UpdateChatTitle->upsert(obj.chatId,obj.title);is TdApi.Error->stage=AuthStage.Failed(obj.message)}}
    private fun onAuth(s:TdApi.AuthorizationState){when(s){is TdApi.AuthorizationStateWaitTdlibParameters->{val cfg=secure.apiConfig()?:run{stage=AuthStage.NeedConfig;return};val p=TdApi.SetTdlibParameters();p.databaseDirectory=File(context.filesDir,"tdlib").absolutePath;p.useMessageDatabase=true;p.useChatInfoDatabase=true;p.useFileDatabase=false;p.useSecretChats=true;p.apiId=cfg.apiId;p.apiHash=cfg.apiHash;p.systemLanguageCode="zh";p.deviceModel="Android";p.applicationVersion="0.2.0";client?.send(p,handler);stage=AuthStage.Starting};is TdApi.AuthorizationStateWaitPhoneNumber->stage=AuthStage.NeedPhone;is TdApi.AuthorizationStateWaitCode->stage=AuthStage.NeedCode;is TdApi.AuthorizationStateWaitPassword->stage=AuthStage.NeedPassword;is TdApi.AuthorizationStateWaitOtherDeviceConfirmation->stage=AuthStage.NeedOtherDevice;is TdApi.AuthorizationStateReady->{stage=AuthStage.Ready;client?.send(TdApi.LoadChats(TdApi.ChatListMain(),100),handler)};else->stage=AuthStage.Failed("未处理的授权状态：${s.javaClass.simpleName}")}}
    private fun upsert(id:Long,title:String){val old=chats.firstOrNull{it.id==id};if(old==null)chats=(chats+RemoteChat(id,title)).sortedBy{it.title}else chats=chats.map{if(it.id==id)it.copy(title=title)else it}}
    private fun toLocal(message:TdApi.Message,chat:RemoteChat):LocalMessage?{val content=message.content as? TdApi.MessageText?:return null;val text=content.text.text.trim();if(text.isBlank())return null;return LocalMessage(remoteId=message.id,chatId=chat.id,chatName=chat.title,sender="Telegram",date=epochText(message.date),text=text)}
}
