package com.yhchat.canary.utils.splitfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分块协议解析的自测。
 *
 * 这些用例直接对应协议文档里写死的约束，改实现时先看这里 ——
 * 尤其是「v2 没有结束消息」「count 由 ceil 推导」「不同发送者不能混任务」这三条，
 * 违反了不会立刻崩，但会让用户的文件拼不回去。
 */
class SplitFileProtocolTest {

    // ----- 清单解析 -----

    @Test
    fun `解析 v2 压缩清单`() {
        val json = """
            {"type":"split_files","v":2,"id":"abc123","name":"movie.mp4",
             "size":1048577,"chunk":349526,"sha256":"${"a".repeat(64)}",
             "md5":"${"b".repeat(32)}","mime":"video/mp4"}
        """.trimIndent()

        val manifest = SplitFileProtocol.parseManifest(json)
        assertNotNull("v2 清单应当能解析", manifest)
        manifest!!
        assertEquals(2, manifest.version)
        assertEquals("abc123", manifest.transferId)
        assertEquals("movie.mp4", manifest.fileName)
        assertEquals(1048577L, manifest.totalSize)
        assertEquals(349526L, manifest.chunkSize)
        // ceil(1048577 / 349526) = 3
        assertEquals(3, manifest.chunkCount)
        assertEquals("video/mp4", manifest.mime)
    }

    @Test
    fun `count 由 size 除以 chunk 向上取整推导`() {
        // 整除的情况
        assertEquals(
            2,
            SplitFileProtocol.parseManifest(
                """{"type":"split_files","v":2,"id":"x","name":"a","size":200,"chunk":100,
                    "sha256":"${"a".repeat(64)}"}"""
            )!!.chunkCount
        )
        // 有余数要进位
        assertEquals(
            3,
            SplitFileProtocol.parseManifest(
                """{"type":"split_files","v":2,"id":"x","name":"a","size":201,"chunk":100,
                    "sha256":"${"a".repeat(64)}"}"""
            )!!.chunkCount
        )
    }

    @Test
    fun `解析 v1 嵌套清单`() {
        // v1 把真正的内容放在 manifest 字段里，外面还包了 protocol / event
        val json = """
            {"type":"split_files","protocol":"v1","event":"manifest",
             "manifest":{"id":"old1","name":"legacy.zip","size":300,"chunk":100,
                         "sha256":"${"c".repeat(64)}"}}
        """.trimIndent()

        val manifest = SplitFileProtocol.parseManifest(json)
        assertNotNull("v1 清单也要能解析（协议要求继续接收）", manifest)
        manifest!!
        assertEquals("old1", manifest.transferId)
        assertEquals("legacy.zip", manifest.fileName)
        assertEquals(3, manifest.chunkCount)
    }

    @Test
    fun `mime 为 octet-stream 时视为未设置`() {
        val json = """{"type":"split_files","v":2,"id":"x","name":"a","size":100,"chunk":100,
                       "sha256":"${"a".repeat(64)}","mime":"application/octet-stream"}"""
        assertNull(SplitFileProtocol.parseManifest(json)!!.mime)
    }

    @Test
    fun `普通的 JSON 聊天内容不会被当成清单`() {
        assertNull(SplitFileProtocol.parseManifest("""{"hello":"world"}"""))
        assertNull(SplitFileProtocol.parseManifest("""{"type":"other"}"""))
        assertNull(SplitFileProtocol.parseManifest("这不是 JSON"))
        assertNull(SplitFileProtocol.parseManifest(null))
        assertNull(SplitFileProtocol.parseManifest(""))
    }

    @Test
    fun `缺少必要字段的清单被拒绝`() {
        // 没有 sha256
        assertNull(
            SplitFileProtocol.parseManifest(
                """{"type":"split_files","v":2,"id":"x","name":"a","size":100,"chunk":100}"""
            )
        )
        // size 是 0
        assertNull(
            SplitFileProtocol.parseManifest(
                """{"type":"split_files","v":2,"id":"x","name":"a","size":0,"chunk":100,
                    "sha256":"${"a".repeat(64)}"}"""
            )
        )
        // size 是小数 —— 协议要求整数，浮点说明发送端实现有问题
        assertNull(
            SplitFileProtocol.parseManifest(
                """{"type":"split_files","v":2,"id":"x","name":"a","size":100.5,"chunk":100,
                    "sha256":"${"a".repeat(64)}"}"""
            )
        )
    }

    // ----- 文件名解析 -----

    @Test
    fun `解析合法的分块文件名`() {
        val hash = "d".repeat(64)
        val name = "yh-split-xyz789-00001-$hash.part"

        val parsed = SplitFileProtocol.parseChunkFileName(name)
        assertNotNull(parsed)
        parsed!!
        assertEquals("xyz789", parsed.transferId)
        assertEquals(1, parsed.chunkIndex)
        assertEquals(hash, parsed.chunkSha256)
    }

    @Test
    fun `块号补零到五位`() {
        val hash = "e".repeat(64)
        val name = SplitFileProtocol.buildChunkFileName("t1", 42, hash)
        assertEquals("yh-split-t1-00042-$hash.part", name)

        // 往返一致
        val parsed = SplitFileProtocol.parseChunkFileName(name)!!
        assertEquals(42, parsed.chunkIndex)
        assertEquals("t1", parsed.transferId)
    }

    @Test
    fun `不合法的分块文件名返回 null`() {
        val hash = "f".repeat(64)
        // 块号不是五位
        assertNull(SplitFileProtocol.parseChunkFileName("yh-split-x-1-$hash.part"))
        // 哈希长度不对
        assertNull(SplitFileProtocol.parseChunkFileName("yh-split-x-00001-abc.part"))
        // 缺少前缀
        assertNull(SplitFileProtocol.parseChunkFileName("x-00001-$hash.part"))
        // 块号为 0（协议从 1 开始）
        assertNull(SplitFileProtocol.parseChunkFileName("yh-split-x-00000-$hash.part"))
        // 普通文件
        assertNull(SplitFileProtocol.parseChunkFileName("家庭合照.jpg"))
        assertNull(SplitFileProtocol.parseChunkFileName(null))
    }

    @Test
    fun `带路径的文件名也能解析`() {
        val hash = "a".repeat(64)
        val parsed = SplitFileProtocol.parseChunkFileName("files/yh-split-t2-00003-$hash.part")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.chunkIndex)
    }

    @Test
    fun `looksLikeChunkFile 只做粗筛不校验格式`() {
        assertTrue(SplitFileProtocol.looksLikeChunkFile("yh-split-随便什么.part"))
        assertFalse(SplitFileProtocol.looksLikeChunkFile("普通文件.txt"))
        assertFalse(SplitFileProtocol.looksLikeChunkFile(null))
    }
}
