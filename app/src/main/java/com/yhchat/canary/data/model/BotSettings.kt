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
    val selectValue: String? = null,
    /**
     * checkbox 的勾选状态，与 [options] 一一对应。
     *
     * 注意服务端只有部分项会给这个字段（有的项第一次读取时没有），
     * 所以是 nullable，读取侧要按 options 长度补齐。
     */
    @SerializedName("selectStatus")
    val selectStatus: List<Boolean>? = null
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
     * 可选项列表。服务端用 `#` 分隔，例如 "文本#Markdown#HTML"。
     *
     * radio / select / checkbox 共用这个字段：
     *   - radio / select 靠 selectIndex 定位当前项；
     *   - checkbox 靠 selectStatus 与它逐位对应。
     *
     * 注意 props 里同名 type 的 value 是空的，真正的选项在 propsValue.options。
     */
    val options: List<String>
        get() = (propsValue?.options ?: "")
            .split('#')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 单选的当前选项文本。
     *
     * radio / select 都用这个。服务端在「未选」时会给 selectIndex = -1，
     * 但 selectValue 里仍带着默认文案，所以不能用 index 反推文本。
     */
    val currentSelectValue: String
        get() = selectValue?.takeIf { it.isNotBlank() }
            ?: options.getOrNull(selectIndex ?: -1).orEmpty()

    /** 当前选中下标；-1 或越界都当"未选中"处理 */
    val currentSelectIndex: Int
        get() {
            val index = selectIndex ?: -1
            return if (index in options.indices) index else -1
        }

    /**
     * 多选状态，长度已对齐 [options]。
     *
     * 服务端有时不给 selectStatus（首次读取），有时给的数组比 options 短，
     * 这里统一补齐成 options 长度，避免界面上出现"选项比状态多"的错位。
     */
    val checkboxStates: List<Boolean>
        get() {
            val states = selectStatus.orEmpty()
            val count = options.size
            if (count == 0) return states
            return List(count) { i -> states.getOrNull(i) ?: false }
        }

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
 * 以字符串 `type` 为准；[fromType] 里把已知的几种映射过来，未知的返回 [Unknown]。
 *
 * key 与 type 的对应关系（来自真实接口样本）：
 *   key=0 → radio    单选框
 *   key=1 → input    单行输入框
 *   key=2 → switch   开关
 *   key=3 → checkbox 多选框
 *   key=4 → textarea 多行输入框
 *   key=5 → select   选择器（下拉）
 */
enum class BotSettingType {
    Switch,
    TextArea,
    Radio,
    Text,
    /** 多选框：值在 selectStatus 里，是布尔数组 */
    Checkbox,
    /** 选择器：语义同 radio，但选项多时更适合下拉展示 */
    Select,
    Unknown;

    companion object {
        fun fromType(type: String, key: Int = 0): BotSettingType = when (type.lowercase()) {
            "switch" -> Switch
            "textarea", "text-area", "text_area" -> TextArea
            "radio" -> Radio
            "select", "dropdown", "picker" -> Select
            "checkbox", "multi-select", "multi_select" -> Checkbox
            "text", "input" -> Text
            // type 缺失时用 key 兜底（与真实接口的编号一致）
            "" -> when (key) {
                0 -> Radio
                1 -> Text
                2 -> Switch
                3 -> Checkbox
                4 -> TextArea
                5 -> Select
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
 * 服务端要的是「id -> 一项」的映射，而不是数组：
 *   {"yofkyi":{"id":"yofkyi","type":"switch","label":"…","value":true}, …}
 *
 * 各控件的提交字段（来自真实请求样本）：
 *   - switch   → "value": true                    布尔
 *   - textarea / input → "value": "..."           字符串
 *   - radio    → "selectIndex": 0, "selectValue": "…"
 *   - select   → "selectIndex": 0, "selectValue": "…"（与 radio 一致）
 *   - checkbox → "selectStatus": [true,false], "selectValues": []
 *
 * 注意同一个 `value` key 在不同控件下类型不同，用 data class 定不住，
 * 所以最终由 [BotSettingSubmitter] 手拼 JsonObject。
 */
data class BotSettingSubmit(
    val id: String,
    val type: String,
    val label: String,
    /** switch 用这个 */
    val booleanValue: Boolean? = null,
    /** textarea / input 用这个 */
    val textValue: String? = null,
    /** radio / select 用这两个 */
    val selectIndex: Int? = null,
    val selectValue: String? = null,
    /** checkbox 用这个，与选项一一对应 */
    val checkboxStates: List<Boolean>? = null
)
