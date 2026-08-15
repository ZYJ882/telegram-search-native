# Telegram Search Native v0.7.0：持续历史同步

v0.7.0 完善了 Telegram 历史消息的连续分页同步机制。同步从最新消息开始，按时间向更早的消息持续读取；索引过程仅在 Telegram 返回空页或手动停止时结束。

## 同步机制

首个请求使用 `from_message_id = 0`，后续请求以上一页中最早消息的 ID 作为新的游标。TDLib 的分页结果可能包含重叠消息或少于请求上限的消息，因此应用通过 `chatId + messageId` 去重，并继续请求后续页面。[1] [2]

| 场景 | 处理方式 |
|---|---|
| 游标向更早消息推进 | 自动请求下一页历史消息。 |
| 页面存在重叠消息 | 去重后继续同步，不将重叠页视为结束。 |
| 游标暂未推进 | 短暂等待后继续请求，保持同步连续性。 |
| Telegram 返回空页 | 判定为当前会话可访问历史的末尾，并继续处理下一个已选会话。 |
| 本地已索引消息 | 不重复写入；扫描进度与新增索引数量分别统计。 |

## 同步体验

同步页面显示当前会话、页码、扫描数量与新增文本索引数量。每个会话均可单独完成历史同步，且支持在当前页完成后停止任务。

## 安装与使用

直接覆盖安装 `TelegramSearchNative-v0.7.0-continuous-sync-debug.apk` 即可保留现有应用数据。进入同步页面后，选择目标会话并启动“同步已选会话的全部历史”。同步过程中建议保持网络连接稳定并使应用处于前台。

## 访问范围

可同步的历史数量由 Telegram 服务端、会话历史可见性、内容保护策略及账号权限共同决定。应用仅索引当前账号可访问的消息内容。

## References

[1] [TDLib: Getting chat messages](https://core.telegram.org/tdlib/getting-started)

[2] [TDLib Issue #236: getChatHistory behavior](https://github.com/tdlib/td/issues/236)
