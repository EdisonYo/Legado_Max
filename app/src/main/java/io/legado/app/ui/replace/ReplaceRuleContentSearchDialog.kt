package io.legado.app.ui.replace

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SearchRequest
import io.legado.app.ui.source.SearchResult
import io.legado.app.ui.source.SearchScopeMode
import io.legado.app.ui.source.SourceBrief
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.ui.source.SourceMetadata
import io.legado.app.utils.GSON
import io.legado.app.utils.share

/**
 * 替换净化规则内容查询界面，用于按规则字段或完整 JSON 搜索替换规则。
 * 数据量通常较小，使用内存搜索。
 */
class ReplaceRuleContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<ReplaceRuleContentSearchViewModel>()

    private var allRules: List<ReplaceRule> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "replace" to "替换",
            "scope" to "作用范围",
            "execute" to "执行"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "group" to "分组",
                "isEnabled" to "启用状态",
                "id" to "ID",
                "order" to "排序"
            ),
            "replace" to listOf(
                "pattern" to "替换内容",
                "replacement" to "替换为",
                "isRegex" to "正则"
            ),
            "scope" to listOf(
                "scopeTitle" to "作用于标题",
                "scopeContent" to "作用于正文",
                "scope" to "作用范围",
                "excludeScope" to "排除范围"
            ),
            "execute" to listOf(
                "timeoutMillisecond" to "超时时间"
            )
        )
    }

    override fun getDialogTitle() = "替换净化规则内容查询"

    override fun getSearchHint() = "输入关键词搜索替换净化规则"

    override fun getContentSearchType() = ContentSearchType.REPLACE_RULE

    override suspend fun loadSourceMetadata(allSources: Boolean): SourceMetadata {
        val rules = viewModel.loadRules(allSources)
        allRules = rules
        cachedJsonStrings = rules.associate { it.id.toString() to GSON.toJson(it) }
        val sources = rules.map {
            SourceBrief(it.getDisplayNameGroup().ifBlank { "未命名(${it.id})" }, it.id.toString())
        }
        val groups = rules.mapNotNull { it.group?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
        return SourceMetadata(sources, groups)
    }

    override suspend fun searchContent(request: SearchRequest): SearchResult {
        val rules = allRules
        if (rules.isEmpty()) return SearchResult(emptyList())

        // 应用范围过滤
        val scopedRules = when (request.scopeMode) {
            SearchScopeMode.SINGLE_SOURCE -> {
                val id = request.selectedSourceUrl ?: return SearchResult(emptyList())
                rules.filter { it.id.toString() == id }
            }
            SearchScopeMode.GROUP -> {
                val group = request.selectedSourceGroup ?: return SearchResult(emptyList())
                rules.filter { it.group == group }
            }
            else -> rules
        }

        if (scopedRules.isEmpty()) return SearchResult(emptyList())

        // 按分类构建 SourceFieldItem
        val tabs = if (request.selectedTab == "__ALL__") {
            TAB_FIELDS
        } else {
            mapOf(request.selectedTab to (TAB_FIELDS[request.selectedTab] ?: emptyList()))
        }

        val items = mutableListOf<SourceFieldItem>()
        for (rule in scopedRules) {
            val ruleId = rule.id.toString()
            val ruleName = rule.getDisplayNameGroup().ifBlank { "未命名($ruleId)" }
            for ((tabKey, fields) in tabs) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(rule, fieldKey) ?: continue
                    if (value.isNotBlank()) {
                        items.add(
                            SourceFieldItem(
                                sourceName = ruleName,
                                sourceUrl = ruleId,
                                tabKey = tabKey,
                                tabName = TAB_NAMES[tabKey] ?: tabKey,
                                fieldKey = fieldKey,
                                fieldName = fieldName,
                                value = value,
                                sourceGroup = rule.group
                            )
                        )
                    }
                }
            }
        }

        // 搜索
        val results = if (request.searchByRuleField) {
            ContentSearchEngine.searchFields(request.query, items)
        } else {
            val jsonItems = scopedRules.mapNotNull { rule ->
                val ruleId = rule.id.toString()
                val json = cachedJsonStrings[ruleId] ?: return@mapNotNull null
                JsonSearchItem(
                    rule.getDisplayNameGroup().ifBlank { "未命名($ruleId)" },
                    ruleId,
                    json
                )
            }
            ContentSearchEngine.searchJson(
                query = request.query,
                sourceItems = items,
                jsonItems = jsonItems
            )
        }

        return SearchResult(results)
    }

    override fun navigateToEdit(sourceUrl: String, tabKey: String?, fieldKey: String?) {
        sourceUrl.toLongOrNull()?.let {
            startActivity(ReplaceEditActivity.startIntent(requireContext(), it))
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        val ruleIds = sourceUrls.mapNotNull { it.toLongOrNull() }
        viewModel.exportRules(ruleIds) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(rule: ReplaceRule, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> rule.name
            "group" -> rule.group
            "isEnabled" -> if (rule.isEnabled) "启用" else "禁用"
            "id" -> rule.id.toString()
            "order" -> rule.order.toString()
            "pattern" -> rule.pattern
            "replacement" -> rule.replacement
            "isRegex" -> if (rule.isRegex) "正则" else "文本"
            "scopeTitle" -> if (rule.scopeTitle) "是" else "否"
            "scopeContent" -> if (rule.scopeContent) "是" else "否"
            "scope" -> rule.scope
            "excludeScope" -> rule.excludeScope
            "timeoutMillisecond" -> rule.timeoutMillisecond.toString()
            else -> null
        }
    }
}
