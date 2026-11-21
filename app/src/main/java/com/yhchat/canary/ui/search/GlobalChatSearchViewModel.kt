package com.yhchat.canary.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.data.model.MessageSender
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.ui.share.SendToChatTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GlobalMessageResult(val target: SendToChatTarget, val message: ChatMessage) {
    val key: String get() = "${target.chatType}:${target.chatId}:${message.msgId}"
}
data class GlobalChatSearchState(
    val results: List<GlobalMessageResult> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)

/** Each conversation/type has its own cursor and buffer; merge only known newest heads. */
@HiltViewModel
class GlobalChatSearchViewModel @Inject constructor(
    private val api: ApiService,
    private val tokens: TokenRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(GlobalChatSearchState())
    val state = mutableState.asStateFlow()
    private data class Source(
        val target: SendToChatTarget, val type: String,
        var cursor: Long = 9999999999999L,
        var exhausted: Boolean = false,
        val buffer: MutableList<GlobalMessageResult> = mutableListOf(),
        val seen: MutableSet<String> = mutableSetOf()
    )
    private var sources = emptyList<Source>()
    private var query = ""
    private var generation = 0
    private var job: Job? = null

    fun search(word: String, targets: Set<SendToChatTarget>, types: Set<Int>) {
        generation++
        job?.cancel()
        query = word.trim()
        sources = targets.flatMap { target ->
            (if (types.isEmpty()) listOf("all") else types.sorted().map { it.toString() })
                .map { Source(target, it) }
        }
        mutableState.value = GlobalChatSearchState(hasMore = query.isNotBlank() && sources.isNotEmpty())
        if (mutableState.value.hasMore) loadMore()
    }

    fun loadMore() {
        if (mutableState.value.loading || !mutableState.value.hasMore) return
        val version = generation
        val currentSources = sources
        val word = query
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            val emitted = mutableListOf<GlobalMessageResult>()
            try {
                val token = tokens.getTokenSync().orEmpty()
                check(token.isNotBlank()) { "请先登录" }
                // Fetch sequentially to bound load. Keep buffers so pagination never inserts
                // newer results above already displayed messages from another conversation.
                repeat(30) {
                    for (source in currentSources) {
                        if (source.buffer.isEmpty() && !source.exhausted) fetch(source, word, token)
                        ensureActive()
                    }
                    val next = currentSources.filter { it.buffer.isNotEmpty() }
                        .maxWithOrNull(compareBy<Source> { it.buffer.first().message.sendTime }
                            .thenBy { it.buffer.first().key }) ?: return@repeat
                    emitted += next.buffer.removeAt(0)
                }
                if (version == generation) {
                    mutableState.value = GlobalChatSearchState(
                        results = (mutableState.value.results + emitted).distinctBy { it.key },
                        hasMore = currentSources.any { !it.exhausted || it.buffer.isNotEmpty() }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Retry is explicit; the UI must not automatically loop on this error.
                if (version == generation) mutableState.value = mutableState.value.copy(
                    results = (mutableState.value.results + emitted).distinctBy { it.key },
                    loading = false, error = error.message ?: "搜索失败"
                )
            }
        }
    }

    private suspend fun fetch(source: Source, word: String, token: String) {
        val body = JSONObject().apply {
            put("keyword", word); put("word", word)
            put("chatId", source.target.chatId); put("chat_id", source.target.chatId)
            put("chatType", source.target.chatType); put("chat_type", source.target.chatType)
            put("type", source.type); put("size", 30); put("time", source.cursor); put("direction", 1)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = api.searchChatMessagesRaw(token, body)
        currentCoroutineContext().ensureActive()
        val payload = response.body()
        check(response.isSuccessful && payload?.code == 1) { payload?.msg ?: "搜索请求失败 (${response.code()})" }
        val rows = payload?.data?.list.orEmpty()
        val fresh = rows.filter { source.seen.add(it.id) }
        source.buffer += fresh.map { row ->
            GlobalMessageResult(source.target, ChatMessage(
                msgId = row.id,
                sender = MessageSender(chatId = row.chatId, chatType = row.chatType,
                    name = row.name, avatarUrl = row.avatarUrl.orEmpty(), tagOld = emptyList(), tag = emptyList()),
                direction = "left", contentType = row.type.toIntOrNull() ?: 1,
                content = MessageContent(text = row.content.takeIf { it.isNotBlank() }),
                sendTime = row.time, cmd = null, msgDeleteTime = null, quoteMsgId = null,
                msgSeq = row.sequence, editTime = null, chatId = source.target.chatId,
                chatType = source.target.chatType, recvId = null
            ))
        }.sortedWith(compareByDescending<GlobalMessageResult> { it.message.sendTime }.thenByDescending { it.key })
        val nextCursor = rows.minOfOrNull { it.time }
        source.exhausted = rows.isEmpty() || fresh.isEmpty() || nextCursor == null
        if (nextCursor != null) source.cursor = nextCursor
    }
}
