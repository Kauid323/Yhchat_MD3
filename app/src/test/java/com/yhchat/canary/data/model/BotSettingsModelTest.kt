package com.yhchat.canary.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 机器人设置表单的模型自测。
 *
 * 样本来自真实接口响应，重点覆盖之前不认识的两类控件：
 *   - checkbox（key=3）：值是布尔数组 selectStatus
 *   - select（key=5）：语义同 radio，但 selectIndex 可能是 -1
 */
class BotSettingsModelTest {

    private val gson = Gson()

    /** 真实响应片段：包含全部 6 种控件 */
    private val sample = """
        {"code":1,"data":{"settingsJson":[
          {"id":"kbdspi","key":1,"props":[{"name":"标签","type":"label","value":""}],"propsValue":{},"title":"Input 输入框","type":"input"},
          {"id":"iqaiyq","key":0,"propsValue":{"label":"单选示例","options":"A#B#C"},"title":"Radio 单选框","type":"radio"},
          {"id":"mfpfqv","key":2,"propsValue":{"label":"开关示例"},"title":"Switch 开关","type":"switch"},
          {"id":"sfsqme","key":3,"propsValue":{"label":"多选示例","options":"X#Y#Z"},"title":"Checkbox 多选框","type":"checkbox"},
          {"id":"yomqga","key":4,"propsValue":{"label":"多行示例"},"title":"textarea 多行输入框","type":"textarea"},
          {"id":"tipont","key":5,"propsValue":{"label":"选择器示例","options":"甲#乙#丙"},"title":"Select 选择器","type":"select"}
        ]},"msg":"success"}
    """.trimIndent()

    private fun parse(json: String): List<BotSettingItem> =
        gson.fromJson(json, BotSettingsResponse::class.java).data!!.settingsJson!!

    @Test
    fun `六种控件都能识别出正确类型`() {
        val items = parse(sample).associateBy { it.id }
        assertEquals(BotSettingType.Text, items["kbdspi"]!!.controlType)
        assertEquals(BotSettingType.Radio, items["iqaiyq"]!!.controlType)
        assertEquals(BotSettingType.Switch, items["mfpfqv"]!!.controlType)
        assertEquals(BotSettingType.Checkbox, items["sfsqme"]!!.controlType)
        assertEquals(BotSettingType.TextArea, items["yomqga"]!!.controlType)
        assertEquals(BotSettingType.Select, items["tipont"]!!.controlType)
    }

    @Test
    fun `propsValue 为空时 label 退回 title`() {
        val item = parse(sample).first { it.id == "kbdspi" }
        // propsValue 是 {}，没有任何字段
        assertEquals("Input 输入框", item.label)
    }

    @Test
    fun `propsValue 有 label 时优先用它`() {
        val item = parse(sample).first { it.id == "iqaiyq" }
        assertEquals("单选示例", item.label)
    }

    @Test
    fun `checkbox 解析 selectStatus 布尔数组`() {
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"14","key":3,"propsValue":{"label":"审核","options":"甲#乙#丙"},
               "selectStatus":[true,false,true],"title":"Checkbox 多选框","type":"checkbox"}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertEquals(listOf(true, false, true), item.checkboxStates)
        // 长度与选项数一致
        assertEquals(item.options.size, item.checkboxStates.size)
    }

    @Test
    fun `checkbox 缺 selectStatus 时按选项数补齐全 false`() {
        // 真实样本里 id=23 就是这种：有 options 但没 selectStatus
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"23","key":3,"propsValue":{"label":"违规拦截","options":"骚扰诈骗#刷屏#色图"},
               "title":"Checkbox 多选框","type":"checkbox","value":""}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertEquals(3, item.options.size)
        assertEquals("没给 selectStatus 时应当补齐成 3 个 false",
            listOf(false, false, false), item.checkboxStates)
    }

    @Test
    fun `checkbox 的 selectStatus 比选项短时也能补齐`() {
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"close","key":3,"propsValue":{"label":"关闭功能","options":"禁用消息撤回#禁用安全功能"},
               "selectStatus":[true],"title":"Checkbox 多选框","type":"checkbox"}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertEquals(listOf(true, false), item.checkboxStates)
    }

    @Test
    fun `select 的 selectIndex 为 -1 时视为未选中`() {
        // 真实样本：s1 的 selectIndex=-1，但 selectValue 有默认文案
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"s1","key":5,"propsValue":{"label":"阶梯式违规处置","options":"甲#乙#丙"},
               "selectIndex":-1,"selectValue":"甲","title":"Select 选择器","type":"select","value":true}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertEquals(-1, item.currentSelectIndex)
        // 文本要能取到，不能因为 index=-1 就丢了
        assertEquals("甲", item.currentSelectValue)
    }

    @Test
    fun `select 的 selectIndex 正常时取对应选项`() {
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"00","key":5,"propsValue":{"label":"协议","options":"同意#不同意"},
               "selectIndex":0,"selectValue":"同意","title":"Select 选择器","type":"select"}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertEquals(0, item.currentSelectIndex)
        assertEquals("同意", item.currentSelectValue)
    }

    @Test
    fun `switch 的 value 是布尔时不崩`() {
        // 这是最早期就踩过的坑：value 声明成 String 会抛异常
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"a","key":2,"propsValue":{"label":"开","defaultValue":"0"},
               "title":"Switch 开关","type":"switch","value":true}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertTrue(item.switchValue)
    }

    @Test
    fun `没有 value 的 switch 退回 defaultValue`() {
        val json = """
            {"code":1,"data":{"settingsJson":[
              {"id":"b","key":2,"propsValue":{"label":"开","defaultValue":"1"},
               "title":"Switch 开关","type":"switch"}
            ]}}
        """.trimIndent()
        val item = parse(json).first()
        assertTrue("defaultValue=1 应当视为开", item.switchValue)
    }

    // ----- 提交格式 -----

    @Test
    fun `checkbox 提交 selectStatus 和 selectValues`() {
        val inner = BotSettingSubmitter.buildInnerJsonString(
            listOf(
                BotSettingSubmit(
                    id = "14",
                    type = "checkbox",
                    label = "审核",
                    checkboxStates = listOf(true, false, true)
                )
            )
        )
        val obj = com.google.gson.JsonParser.parseString(inner)
            .asJsonObject.getAsJsonObject("14")
        assertTrue(obj.getAsJsonArray("selectStatus").get(0).asBoolean)
        assertFalse(obj.getAsJsonArray("selectStatus").get(1).asBoolean)
        // 真实请求里这两个字段总是同时出现
        assertTrue(obj.has("selectValues"))
        assertTrue(obj.getAsJsonArray("selectValues").size() == 0)
    }

    @Test
    fun `select 提交 selectIndex 和 selectValue 而不是 value`() {
        val inner = BotSettingSubmitter.buildInnerJsonString(
            listOf(
                BotSettingSubmit(
                    id = "s1",
                    type = "select",
                    label = "设置1",
                    selectIndex = 2,
                    selectValue = "丙"
                )
            )
        )
        val obj = com.google.gson.JsonParser.parseString(inner)
            .asJsonObject.getAsJsonObject("s1")
        assertEquals(2, obj.get("selectIndex").asInt)
        assertEquals("丙", obj.get("selectValue").asString)
        // select 不该带 value
        assertFalse("select 不应提交 value 字段", obj.has("value"))
    }

    @Test
    fun `switch 提交布尔而不是字符串`() {
        val inner = BotSettingSubmitter.buildInnerJsonString(
            listOf(
                BotSettingSubmit(
                    id = "sw",
                    type = "switch",
                    label = "开关",
                    booleanValue = true
                )
            )
        )
        val obj = com.google.gson.JsonParser.parseString(inner)
            .asJsonObject.getAsJsonObject("sw")
        assertTrue("switch 的 value 必须是布尔", obj.get("value").asBoolean)
    }

    @Test
    fun `input 提交字符串 value`() {
        val inner = BotSettingSubmitter.buildInnerJsonString(
            listOf(
                BotSettingSubmit(
                    id = "in",
                    type = "input",
                    label = "输入",
                    textValue = "hello"
                )
            )
        )
        val obj = com.google.gson.JsonParser.parseString(inner)
            .asJsonObject.getAsJsonObject("in")
        assertEquals("hello", obj.get("value").asString)
    }
}
