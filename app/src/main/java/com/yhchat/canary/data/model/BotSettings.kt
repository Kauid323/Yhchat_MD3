package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 机器人自定义设置（服务端下发的一整套表单）。
 *
 * 结构是「扁平的表单项数组」，每一项自己描述自己该用什么控件画：
 * `type` 决定控件，`propsValue.label` 是显示名，`propsValue.placeholder` 是占位提示，
 * `value` / `selectValue` / `selectIndex` 是当前值。
 *
 * 所以客户端不需要知道任何一个具体设置项是什么 —— 全部按 type 渲染，
 * 服务端加新项、改文案都不用改客户端。
 */
data class BotSettingsResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("data")
    val data: BotSettingsData? = null,
    @SerializedName("msg")
    val msg: String = ""
)

data class BotSettingsData(
    @SerializedName("settingsJson")
    val settingsJson: List<BotSettingItem>? = null
)

/**
 * 一个表单项。
 *
 * `key` 是控件种类编号（0=单选 2=开关 4=多行文本，见 [BotSettingType]）；
 * `type` 是字符串形式，取值 switch / textarea / radio / ...，判断时优先用它 ——
 * 编号在新版本里可能会变，字符串是稳定契约。
 */
data class BotSettingItem(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("key")
    val key: Int = 0,
    @SerializedName("type")
    val type: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("props")
    val props: List<BotSettingProp>? = null,
    @SerializedName("propsValue")
    val propsValue: BotSettingPropsValue? = null,
    /**
     * 当前值。**类型随控件变**：switch 是布尔，textarea / text 是字符串，
     * radio 干脆没有这个字段。所以这里必须用 JsonElement 接，
     * 声明成 String 的话 Gson 遇到 `"value": true` 会抛 IllegalStateException，
     * 整个设置页直接打不开。
     */
    @SerializedName("value")
    val value: com.google.gson.JsonElement? = null,
    @SerializedName("selectIndex")
    val selectIndex: Int? = null,
    @SerializedName("selectValue")
    val selectValue: String? = null
) {
    /** 显示名优先用 propsValue.label；它为空时退回 title，再退回 id */
    val label: String
        get() = propsValue?.label?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it.isNotBlank() }
            ?: id

    /** 占位提示。整个过程都是可选的，没有就不显示 */
    val placeholder: String
        get() = propsValue?.placeholder?.takeIf { it.isNotBlank() }
            ?: props?.firstOrNull { it.type == "placeholder" }?.placeholder
            ?: ""

    /**
     * 单选的可选项。服务端用 `#` 分隔，例如 "文本#Markdown#HTML"。
     *
     * 注意 props 里同名 type 的 value 是空的，真正的选项在 propsValue.options。
     */
    val options: List<String>
        get() = (propsValue?.options ?: "")
            .split('#')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 文本值。布尔或 null 都当成空串 —— 只有文本控件会读它 */
    val textValue: String
        get() = when {
            value == null || value.isJsonNull -> ""
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
            // 极少数情况下服务端把数字/布尔塞给了文本控件，转成字符串照常显示
            value.isJsonPrimitive -> value.asString
            else -> ""
        }

    /** 开关的当前值 */
    val switchValue: Boolean
        get() = when {
            value == null || value.isJsonNull -> {
                // 没有 value 时退回「默认状态」：1 开，0 关
                propsValue?.defaultValue == "1"
            }
            value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
            // 有的机器人用 "1"/"0" 表示开关
            value.isJsonPrimitive -> value.asString == "1" ||
                value.asString.equals("true", ignoreCase = true)
            else -> false
        }
}

/**
 * 表单项的属性说明。
 *
 * 这层结构主要是给服务端自己看的元信息（"标签"是什么、"占位文本"是什么），
 * 客户端真正用到的只有 name/type/value 里的少数几个。
 */
data class BotSettingProp(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("value")
    val value: String = "",
    @SerializedName("placeholder")
    val placeholder: String = ""
)

data class BotSettingPropsValue(
    @SerializedName("label")
    val label: String = "",
    @SerializedName("placeholder")
    val placeholder: String = "",
    @SerializedName("options")
    val options: String = "",
    @SerializedName("defaultValue")
    val defaultValue: String = ""
)

/**
 * 控件种类。
 *
 * 以字符串 `type` 为准；[fromType] 里把已知的几种映射过来，未知的返回 [Unknown]，
 * 调用方据此决定"不渲染"还是"当文本渲染"。
 */
enum class BotSettingType {
    Switch,
    TextArea,
    Radio,
    Text,
    Unknown;

    companion object {
        fun fromType(type: String, key: Int = 0): BotSettingType = when (type.lowercase()) {
            "switch" -> Switch
            "textarea", "text-area", "text_area" -> TextArea
            "radio" -> Radio
            "text", "input" -> Text
            // type 缺失时用 key 兜底：0=单选 2=开关 4=多行文本
            "" -> when (key) {
                0 -> Radio
                2 -> Switch
                4 -> TextArea
                else -> Unknown
            }
            else -> Unknown
        }
    }
}

/** 把设置项解析成可渲染的控件类型 */
val BotSettingItem.controlType: BotSettingType
    get() = BotSettingType.fromType(type, key)

/**
 * 提交时每一项的形状。
 *
 * 服务端要的是「id -> 一项」的映射，而不是数组，所以这里用树形结构再序列化：
 *   {"yofkyi":{"id":"yofkyi","type":"switch","label":"…","value":true}, …}
 *
 * 类型差异（这条容易踩）：switch 的 value 是**布尔**，其余是**字符串**；
 * radio 走 selectIndex + selectValue 而不是 value。少一个字段那一项就保存不上。
 *
 * 因为同一个 `value` key 可能是布尔也可能是字符串，用 data class 定不住类型，
 * 所以提交时直接拼 JsonObject，见 [BotSettingSubmitter]。
 */
data class BotSettingSubmit(
    val id: String,
    val type: String,
    val label: String,
    /** switch 用这个 */
    val booleanValue: Boolean? = null,
    /** textarea / text 用这个 */
    val textValue: String? = null,
    /** radio 用这两个 */
    val selectIndex: Int? = null,
    val selectValue: String? = null
)
