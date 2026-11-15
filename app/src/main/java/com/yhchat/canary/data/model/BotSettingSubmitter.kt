package com.yhchat.canary.data.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 把编辑后的表单拼成服务端要的提交格式。
 *
 * 提交体的形状是双层的：
 * ```
 * {
 *   "id": "<botId>",
 *   "groupId": "<groupId>",
 *   "settingJson": "{\"<项id>\":{\"id\":…,\"type\":…,\"label\":…,\"value\":…}, …}"
 * }
 * ```
 * 内层是「id -> 一项」的映射，而且要**再序列化成字符串**塞进 settingJson 字段。
 *
 * 每个控件的字段差异（全部来自真实请求样本）：
 *   - switch   → {"value": true}                      布尔
 *   - input / textarea → {"value": "..."}             字符串
 *   - radio / select   → {"selectIndex": 0, "selectValue": "…"}，不带 value
 *   - checkbox → {"selectStatus": [true,false], "selectValues": []}
 *
 * 因为是同名字段承载不同类型，只能逐项手拼 JsonObject。
 */
object BotSettingSubmitter {

    private val gson = Gson()

    /** 拼内层 settingJson 字符串 */
    fun buildInnerJsonString(items: List<BotSettingSubmit>): String {
        val root = JsonObject()
        items.forEach { item ->
            val entry = JsonObject().apply {
                addProperty("id", item.id)
                addProperty("type", item.type)
                addProperty("label", item.label)

                // 用统一的 BotSettingType 判断，而不是裸比较字符串：
                // 服务端可能写 "Switch"、"textarea"、"text-area"，
                // 裸 lowercase() 比较会漏掉别名，那一项就带着错误的值提交上去
                when (BotSettingType.fromType(item.type)) {
                    BotSettingType.Switch ->
                        addProperty("value", item.booleanValue ?: false)

                    BotSettingType.Radio, BotSettingType.Select -> {
                        // radio / select 不提交 value，只提交索引和选项文本
                        item.selectIndex?.let { addProperty("selectIndex", it) }
                        addProperty("selectValue", item.selectValue.orEmpty())
                    }

                    BotSettingType.Checkbox -> {
                        // checkbox 提交布尔数组，外加一个（服务端会忽略的）空 selectValues。
                        // 真实请求样本里两者总是同时出现，缺了 selectValues 可能被判定为格式不符，
                        // 所以照抄。
                        val statuses = com.google.gson.JsonArray()
                        item.checkboxStates.orEmpty().forEach { statuses.add(it) }
                        add("selectStatus", statuses)
                        add("selectValues", com.google.gson.JsonArray())
                    }

                    else -> addProperty("value", item.textValue.orEmpty())
                }
            }
            root.add(item.id, entry)
        }
        return gson.toJson(root)
    }

    /** 拼完整请求体 */
    fun buildRequest(botId: String, groupId: String, items: List<BotSettingSubmit>): JsonObject =
        JsonObject().apply {
            // 注意字段名是 id，不是 botId —— 跟读取接口不一致，别抄错
            addProperty("id", botId)
            addProperty("groupId", groupId)
            addProperty("settingJson", buildInnerJsonString(items))
        }
}
