package com.yhchat.canary.ui.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.data.repository.ExternalShareRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExternalSharePayload(
    val text: String? = null,
    val uri: Uri? = null,
    val mimeType: String = "text/plain",
    val subject: String? = null
)

data class ExternalShareTargetApp(
    val packageName: String,
    val label: String,
    val resolveInfo: ResolveInfo
)

object ExternalShareUtils {
    const val EXTRA_SCOPE_CHAT_ID = "com.yhchat.canary.extra.SHARE_CHAT_ID"
    private const val EXTRA_TARGET_LABEL = "com.yhchat.canary.extra.SHARE_TARGET_LABEL"

    fun shareText(context: Context, text: String, chatId: String? = null) {
        share(
            context = context,
            payload = ExternalSharePayload(
                text = text,
                mimeType = "text/plain",
                subject = "YhChat 文本"
            ),
            chatId = chatId
        )
    }

    fun shareMessage(context: Context, message: ChatMessage) {
        share(context, message.toExternalSharePayload(), message.chatId ?: message.recvId)
    }

    fun share(context: Context, payload: ExternalSharePayload, chatId: String? = null) {
        if (payload.text.isNullOrBlank() && payload.uri == null) {
            Toast.makeText(context, "没有可分享的内容", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val repository = ExternalShareRepository(context)
            val preferred = withContext(Dispatchers.IO) { repository.getResolvedTarget(chatId) }
            val baseIntent = buildSendIntent(payload)
            val packageManager = context.packageManager
            val directPackage = preferred?.packageName?.takeIf { canHandle(packageManager, baseIntent, it) }

            if (directPackage != null) {
                val directIntent = Intent(baseIntent).setPackage(directPackage)
                runCatching {
                    ContextCompat.startActivity(context, directIntent.addNewTaskIfNeeded(context), null)
                }.onFailure {
                    launchChooser(context, baseIntent, chatId)
                }
            } else {
                launchChooser(context, baseIntent, chatId)
            }
        }
    }

    fun queryShareTargets(context: Context, mimeType: String = "text/plain"): List<ExternalShareTargetApp> {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, " ")
        }
        return context.packageManager.queryIntentActivitiesCompat(intent)
            // 本应用自己也是 ACTION_SEND 的接收方（发送至），不要出现在「分享到外部应用」列表里
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                ExternalShareTargetApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank { info.activityInfo.packageName },
                    resolveInfo = info
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getAppLabel(context: Context, packageName: String): String {
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun launchChooser(context: Context, baseIntent: Intent, chatId: String?) {
        val callback = Intent(context, ExternalShareChosenReceiver::class.java).apply {
            putExtra(EXTRA_SCOPE_CHAT_ID, chatId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else 0
        val pendingIntent = PendingIntent.getBroadcast(context, (System.currentTimeMillis() and 0xfffffff).toInt(), callback, flags)
        val chooser = Intent.createChooser(baseIntent, "分享到", pendingIntent.intentSender)
        runCatching {
            ContextCompat.startActivity(context, chooser.addNewTaskIfNeeded(context), null)
        }.onFailure {
            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSendIntent(payload: ExternalSharePayload): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = payload.mimeType
            payload.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            payload.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            payload.uri?.let { uri ->
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun canHandle(packageManager: PackageManager, baseIntent: Intent, packageName: String): Boolean {
        val probe = Intent(baseIntent).setPackage(packageName)
        return packageManager.queryIntentActivitiesCompat(probe).isNotEmpty()
    }

    private fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    private fun Intent.addNewTaskIfNeeded(context: Context): Intent {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return this
    }

    fun ChatMessage.toExternalSharePayload(): ExternalSharePayload {
        val content = content
        return when (contentType) {
            2 -> content.imageUrl.toPayloadOrText("image/*", "图片消息")
            7 -> (content.stickerUrl ?: content.imageUrl).toPayloadOrText("image/*", "表情消息")
            4, 6 -> content.fileUrl.toPayloadOrText("*/*", content.fileName ?: "文件消息", content.fileName)
            10 -> content.videoUrl.toPayloadOrText("video/*", "视频消息")
            11 -> content.audioUrl.toPayloadOrText("audio/*", "语音消息")
            14 -> ExternalSharePayload(
                text = content.a2UiShareText(),
                mimeType = "text/plain",
                subject = "YhChat A2UI"
            )
            else -> ExternalSharePayload(
                text = content.operationText(contentType).ifBlank { Gson().toJson(content) },
                mimeType = "text/plain",
                subject = "YhChat 消息"
            )
        }
    }

    fun MessageContent.operationText(contentType: Int): String {
        return when (contentType) {
            1, 3, 8 -> text.orEmpty()
            14 -> a2UiShareText()
            2 -> imageUrl.orEmpty()
            7 -> stickerUrl ?: imageUrl.orEmpty()
            4, 6 -> listOfNotNull(fileName, fileUrl).joinToString("\n")
            10 -> videoUrl.orEmpty()
            11 -> audioUrl.orEmpty()
            else -> listOfNotNull(text, imageUrl, fileUrl, videoUrl, audioUrl).firstOrNull { it.isNotBlank() }.orEmpty()
        }
    }

    private fun MessageContent.a2UiShareText(): String {
        return buildString {
            text?.takeIf { it.isNotBlank() }?.let { append(it) }
            buttons?.takeIf { it.isNotBlank() && it != text }?.let {
                if (isNotEmpty()) append("\n")
                append(it)
            }
        }
    }

    private fun String?.toPayloadOrText(mimeType: String, subject: String, label: String? = null): ExternalSharePayload {
        val value = this?.takeIf { it.isNotBlank() } ?: return ExternalSharePayload(text = label ?: subject, subject = subject)
        val uri = value.toShareableUri()
        return if (uri != null) {
            ExternalSharePayload(uri = uri, mimeType = mimeType, subject = subject, text = label)
        } else {
            ExternalSharePayload(text = value, mimeType = "text/plain", subject = subject)
        }
    }

    private fun String.toShareableUri(): Uri? {
        val parsed = runCatching { Uri.parse(this) }.getOrNull() ?: return null
        return when (parsed.scheme?.lowercase()) {
            "content" -> parsed
            "file" -> parsed
            else -> null
        }
    }
}

class ExternalShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val component: ComponentName? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }
        val packageName = component?.packageName ?: return
        val chatId = intent.getStringExtra(ExternalShareUtils.EXTRA_SCOPE_CHAT_ID)
        val label = ExternalShareUtils.getAppLabel(context, packageName)
        CoroutineScope(Dispatchers.IO).launch {
            ExternalShareRepository(context).saveResolvedTarget(chatId, packageName, label)
        }
    }
}
