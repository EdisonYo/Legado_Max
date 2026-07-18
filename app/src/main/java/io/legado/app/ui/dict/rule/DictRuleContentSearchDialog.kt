package io.legado.app.ui.dict.rule

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.DictRule
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
import io.legado.app.utils.showDialogFragment

/**
 * 字典规则内容查询界面，用于按规则字段或完整 JSON 搜索字典规则。
 * 数据量通常较小，使用内存搜索。
 */
class DictRuleContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<DictRuleContentSearchViewModel>()

    private var allRules: List<DictRule> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "rule" to "规则"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "enabled" to "启用状态",
                "sortNumber" to "排序"
            ),
            "rule" to listOf(
                "urlRule" to "URL规则",
                "showRule" to "显示规则"
            )
        )
    }

    override fun getDialogTitle() = "字典规则内容查询"

    override fun getSearchHint() = "输入关键词搜索字典规则"

    override fun getContentSearchType() = ContentSearchType.DICT_RULE

    override suspend fun loadSourceMetadata(allSources: Boolean): SourceMetadata {
        val rules = viewModel.loadRules(allSources)
        allRules = rules
        cachedJsonStrings = rules.associate { it.name to GSON.toJson(it) }
        val sources = rules.map { SourceBrief(it.name.ifBlank { "未命名" }, it.name) }
        return SourceMetadata(sources, emptyList())
    }

    override suspend fun searchContent(request: SearchRequest): SearchResult {
        val rules = allRules
        if (rules.isEmpty()) return SearchResult(emptyList())

        // 应用范围过滤
        val scopedRules = when (request.scopeMode) {
            SearchScopeMode.SINGLE_SOURCE -> {
                val name = request.selectedSourceUrl ?: return SearchResult(emptyList())
                rules.filter { it.name == name }
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
            val ruleName = rule.name.ifBlank { "未命名" }
            for ((tabKey, fields) in tabs) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(rule, fieldKey) ?: continue
                    if (value.isNotBlank()) {
                        items.add(
                            SourceFieldItem(
                                sourceName = ruleName,
                                sourceUrl = rule.name,
                                tabKey = tabKey,
                                tabName = TAB_NAMES[tabKey] ?: tabKey,
                                fieldKey = fieldKey,
                                fieldName = fieldName,
                                value = value
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
                val json = cachedJsonStrings[rule.name] ?: return@mapNotNull null
                JsonSearchItem(rule.name.ifBlank { "未命名" }, rule.name, json)
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
        showDialogFragment(DictRuleEditDialog(sourceUrl))
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        viewModel.exportRules(sourceUrls) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(rule: DictRule, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> rule.name
            "enabled" -> if (rule.enabled) "启用" else "禁用"
            "sortNumber" -> rule.sortNumber.toString()
            "urlRule" -> rule.urlRule
            "showRule" -> rule.showRule
            else -> null
        }
    }
}
