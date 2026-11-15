package com.yhchat.canary.utils.splitfile

import com.yhchat.canary.data.model.ChatMessage
import com.yhchat.canary.data.model.MessageContent
import com.yhchat.canary.data.model.MessageSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分块索引的自测。
 *
 * 重点覆盖协议里三条"违反了不会立刻崩、但文件会拼不回去"的约束：
 *   1. 不同发送者 / 不同会话的分块不能混进同一任务；
 *   2. 重复索引去重、冲突索引报错；
 *   3. 撤回的文件不纳入索引，且要考虑"先索引后撤回"的顺序。
 */
class SplitFileIndexTest {

    private val sha = "a".repeat(64)
    private val chunkHash = "b".repeat(64)

    private fun manifestJson(id: String, size: Long = 300, chunk: Long = 100): String =
        """{"type":"split_files","v":2,"id":"$id","name":"file.bin",
            "size":$size,"chunk":$chunk,"sha256":"$sha"}"""

    private fun textMessage(
        msgId: String,
        text: String,
        chatId: String = "group1",
        chatType: Int = 2,
        senderId: String = "user1"
    ) = ChatMessage(
        msgId = msgId,
        sender = MessageSender(
            chatId = senderId,
            chatType = 1,
            name = "sender",
            avatarUrl = ""
        ),
        direction = "left",
        contentType = 1,
        content = MessageContent(text = text),
        sendTime = 0L,
        chatId = chatId,
        chatType = chatType
    )

    private fun chunkMessage(
        msgId: String,
        fileName: String,
        fileUrl: String,
        size: Long = 100,
        chatId: String = "group1",
        chatType: Int = 2,
        senderId: String = "user1"
    ) = ChatMessage(
        msgId = msgId,
        sender = MessageSender(
            chatId = senderId,
            chatType = 1,
            name = "sender",
            avatarUrl = ""
        ),
        direction = "left",
        contentType = 5,
        content = MessageContent(
            fileName = fileName,
            fileUrl = fileUrl,
            fileSize = size
        ),
        sendTime = 0L,
        chatId = chatId,
        chatType = chatType
    )

    @Test
    fun `清单与分块聚合成一个任务`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))
        index.offer(
            chunkMessage("m1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/key1")
        )
        index.offer(
            chunkMessage("m2", "yh-split-t1-00002-$chunkHash.part", "https://cdn/key2")
        )

        val tasks = index.snapshot()
        assertEquals(1, tasks.size)
        assertEquals(2, tasks[0].receivedCount)
        // 清单说 300/100 = 3 块，只收到 2 块
        assertEquals(1, tasks[0].missingCount)
        assertEquals(listOf(3), tasks[0].missingIndexes)
    }

    @Test
    fun `分块先到也能在清单到达后聚合成任务`() {
        val index = SplitFileIndex()
        // 实时/倒序：分块先来
        val first = index.offer(
            chunkMessage("m1", "yh-split-t9-00001-$chunkHash.part", "https://cdn/k1")
        )
        assertTrue(first is SplitOfferResult.ChunkAdded)

        // 此时清单还没到，不应算作完整任务
        assertTrue(index.snapshot().isEmpty())

        index.offer(textMessage("m0", manifestJson("t9")))
        val tasks = index.snapshot()
        assertEquals(1, tasks.size)
        assertEquals(1, tasks[0].receivedCount)
    }

    @Test
    fun `不同发送者的相同 transferId 不会混任务`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("same"), senderId = "alice"))
        index.offer(
            chunkMessage(
                "m1", "yh-split-same-00001-$chunkHash.part", "https://cdn/k1",
                senderId = "alice"
            )
        )
        // bob 用了同一个 transferId，但他是另一个发送者
        index.offer(
            chunkMessage(
                "m2", "yh-split-same-00001-$chunkHash.part", "https://cdn/k2",
                senderId = "bob"
            )
        )

        val tasks = index.snapshot()
        // 只有 alice 有清单，所以只有一个任务，且只含她自己的那块
        assertEquals(1, tasks.size)
        assertEquals("alice", tasks[0].key.senderId)
        assertEquals(1, tasks[0].receivedCount)
    }

    @Test
    fun `不同会话的相同 transferId 不会混任务`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("dup"), chatId = "groupA"))
        index.offer(
            chunkMessage("m1", "yh-split-dup-00001-$chunkHash.part", "https://cdn/k1", chatId = "groupA")
        )
        index.offer(
            chunkMessage("m2", "yh-split-dup-00001-$chunkHash.part", "https://cdn/k2", chatId = "groupB")
        )

        val tasks = index.snapshot()
        assertEquals(1, tasks.size)
        assertEquals("groupA", tasks[0].key.chatId)
    }

    @Test
    fun `重复的分块消息被去重`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))

        val name = "yh-split-t1-00001-$chunkHash.part"
        index.offer(chunkMessage("m1", name, "https://cdn/k1"))
        // 同一条消息再喂一次
        val again = index.offer(chunkMessage("m1", name, "https://cdn/k1"))

        assertEquals(SplitOfferResult.Duplicate, again)
        assertEquals(1, index.snapshot()[0].receivedCount)
    }

    @Test
    fun `同一块号不同资源视为冲突且保留先到的`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))

        index.offer(
            chunkMessage("m1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/AAA")
        )
        val result = index.offer(
            chunkMessage("m2", "yh-split-t1-00001-$chunkHash.part", "https://cdn/BBB")
        )

        assertTrue("应判定为冲突", result is SplitOfferResult.ChunkConflict)

        val task = index.snapshot()[0]
        assertEquals(1, task.conflicts.size)
        assertEquals("m1", task.conflicts[0].existingMsgId)
        assertEquals("m2", task.conflicts[0].incomingMsgId)
        // 冲突时任务不该被认为可合并
        assertTrue(!task.isReadyToMerge)
        // 保留先到的那块
        assertEquals("AAA", task.chunks[0].resourceKey)
    }

    @Test
    fun `撤回的消息不纳入索引`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))
        index.markRecalled("m1")

        val result = index.offer(
            chunkMessage("m1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1")
        )
        assertEquals(SplitOfferResult.Ignored, result)
        assertEquals(0, index.snapshot()[0].receivedCount)
    }

    @Test
    fun `先索引后撤回会把已索引的分块剔除`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))
        index.offer(
            chunkMessage("m1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1")
        )
        assertEquals(1, index.snapshot()[0].receivedCount)

        index.markRecalled("m1")
        assertEquals(0, index.snapshot()[0].receivedCount)
    }

    @Test
    fun `缺块数量与块号列表正确`() {
        val index = SplitFileIndex()
        // 5 块，只给第 1、3、5 块
        index.offer(textMessage("m0", manifestJson("t5", size = 500, chunk = 100)))
        listOf(1, 3, 5).forEach { i ->
            index.offer(
                chunkMessage(
                    "c$i",
                    "yh-split-t5-${i.toString().padStart(5, '0')}-$chunkHash.part",
                    "https://cdn/k$i"
                )
            )
        }

        val task = index.snapshot()[0]
        assertEquals(3, task.receivedCount)
        assertEquals(2, task.missingCount)
        assertEquals(listOf(2, 4), task.missingIndexes)
        assertTrue(!task.isReadyToMerge)
    }

    @Test
    fun `块齐但未校验时只是可合并`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1", size = 200, chunk = 100)))
        index.offer(chunkMessage("c1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1"))
        index.offer(chunkMessage("c2", "yh-split-t1-00002-$chunkHash.part", "https://cdn/k2"))

        val task = index.snapshot()[0]
        assertEquals(0, task.missingCount)
        // 块齐即"可下载"，完整性校验在合并阶段做
        assertTrue(task.isReadyToMerge)
    }

    @Test
    fun `缺失大小的分块被忽略`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))
        index.offer(
            chunkMessage(
                "m1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1", size = 0
            )
        )
        assertEquals(0, index.snapshot()[0].receivedCount)
    }

    @Test
    fun `clear 之后索引为空`() {
        val index = SplitFileIndex()
        index.offer(textMessage("m0", manifestJson("t1")))
        index.offer(chunkMessage("c1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1"))
        assertEquals(1, index.snapshot().size)

        index.clear()
        assertTrue(index.snapshot().isEmpty())
    }

    // ----- 显示模式判定 -----

    @Test
    fun `清单显示为卡片`() {
        val adapter = SplitFileAdapter()
        val mode = adapter.displayModeOf(textMessage("m0", manifestJson("t1")))
        assertTrue(mode is SplitDisplayMode.ManifestCard)
    }

    @Test
    fun `清单未到时孤立分块显示为普通文件`() {
        val adapter = SplitFileAdapter()
        adapter.offer(
            chunkMessage("c1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1")
        )

        val mode = adapter.displayModeOf(
            chunkMessage("c1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1")
        )
        // 协议：清单尚未加载的孤立分块仍显示为普通文件
        assertTrue("孤立分块不该变成卡片", mode is SplitDisplayMode.OrphanChunk)
    }

    @Test
    fun `清单到齐后分块折叠进任务`() {
        val adapter = SplitFileAdapter()
        val chunk = chunkMessage("c1", "yh-split-t1-00001-$chunkHash.part", "https://cdn/k1")
        adapter.offer(chunk)
        adapter.offer(textMessage("m0", manifestJson("t1")))

        val mode = adapter.displayModeOf(chunk)
        assertTrue("清单到了之后应属于任务", mode is SplitDisplayMode.PartOfTask)
    }

    @Test
    fun `普通消息永远是 Normal`() {
        val adapter = SplitFileAdapter()
        val mode = adapter.displayModeOf(
            textMessage("m0", "今天天气不错")
        )
        assertEquals(SplitDisplayMode.Normal, mode)
    }
}
