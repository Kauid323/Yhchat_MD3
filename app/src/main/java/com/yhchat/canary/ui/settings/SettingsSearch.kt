package com.yhchat.canary.ui.settings

/**
 * 设置搜索的匹配逻辑。
 *
 * 只做「子串 + 打分」，不引第三方分词库 —— 设置项标题都很短，条目量在百条量级，
 * 不需要倒排索引。打分规则：
 *
 *   100  title 完全等于查询
 *    80  title 以查询开头
 *    60  title 包含查询
 *    40  keyword 命中
 *    20  section 命中（能搜「主题」找到该分组下所有条目）
 *
 * 每多一个命中词加一点，保证「主题 颜色」这种多词查询把最相关的排前面。
 */

/** 一条命中结果。 */
internal data class SettingsSearchHit(
    val entry: SettingsEntry,
    val score: Int,
    /** 命中的是哪个字段，给副标题用（title / 关键词 / 分组）。 */
    val matchedOn: String
)

internal object SettingsSearch {

    /**
     * 搜索。
     *
     * 查询按空格切成多个词，要求**每个词**都能在条目里找到（AND），
     * 这样「主题 颜色」不会把只含「主题」的条目也带出来。任意一个词都找不到
     * 就整体不命中。
     *
     * @param query 用户输入，空串返回空列表
     */
    fun search(query: String): List<SettingsSearchHit> {
        val terms = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<SettingsSearchHit>()

        SettingsSearchIndex.forEachIndexed { index, entry ->
            var total = 0
            var matchedOn = ""

            for (term in terms) {
                val best = scoreFor(entry, term)
                // 有一个词完全找不到，这条就不要 —— AND 语义
                if (best.score == 0) return@forEachIndexed

                total += best.score
                if (matchedOn.isEmpty()) matchedOn = best.on
            }

            // 索引里越靠前的越优先，用它当微弱的次序修正；
            // 乘 100 是为了让这个修正永远小于一个词的得分差
            hits += SettingsSearchHit(
                entry = entry,
                score = total * 100 - index,
                matchedOn = matchedOn
            )
        }

        return hits.sortedByDescending { it.score }
    }

    /** 单词匹配：返回得分和命中字段。 */
    private fun scoreFor(entry: SettingsEntry, term: String): Pair<Int, String> {
        val title = entry.title.lowercase()
        return when {
            title == term -> 100 to "title"
            title.startsWith(term) -> 80 to "title"
            title.contains(term) -> 60 to "title"
            entry.keywords.any { it.lowercase().contains(term) } -> 40 to "keyword"
            entry.section.lowercase().contains(term) -> 20 to "section"
            else -> 0 to ""
        }
    }
}