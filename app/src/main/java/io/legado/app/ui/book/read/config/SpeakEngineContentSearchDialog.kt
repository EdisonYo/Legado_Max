package io.legado.app.ui.book.read.config

import androidx.fragment.app.viewModels
import io.legado.app.data.entities.HttpTTS
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
 * 朗读引擎内容查询界面，用于按规则字段或完整 JSON 搜索 HTTP TTS 配置。
 * 数据量通常较小，使用内存搜索。
 */
class SpeakEngineContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<SpeakEngineContentSearchViewModel>()

    private var allEngines: List<HttpTTS> = emptyList()
    private var cachedJsonStrings: Map<String, String> = emptyMap()

    companion object {
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "request" to "请求",
            "login" to "登录",
            "script" to "脚本"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "name" to "名称",
                "id" to "ID",
                "lastUpdateTime" to "更新时间"
            ),
            "request" to listOf(
                "url" to "URL",
                "contentType" to "Content-Type",
                "header" to "请求头",
                "concurrentRate" to "并发率",
                "enabledCookieJar" to "CookieJar"
            ),
            "login" to listOf(
                "loginUrl" to "登录URL",
                "loginUi" to "登录UI",
                "loginCheckJs" to "登录检查JS"
            ),
            "script" to listOf(
                "jsLib" to "JS库"
            )
        )
    }

    override fun getDialogTitle() = "朗读引擎规则内容查询"

    override fun getSearchHint() = "输入关键词搜索朗读引擎规则"

    override fun getContentSearchType() = ContentSearchType.SPEAK_ENGINE

    override suspend fun loadSourceMetadata(allSources: Boolean): SourceMetadata {
        val engines = viewModel.loadEngines()
        allEngines = engines
        cachedJsonStrings = engines.associate { it.id.toString() to GSON.toJson(it) }
        val sources = engines.map {
            SourceBrief(it.name.ifBlank { "未命名(${it.id})" }, it.id.toString())
        }
        return SourceMetadata(sources, emptyList())
    }

    override suspend fun searchContent(request: SearchRequest): SearchResult {
        val engines = allEngines
        if (engines.isEmpty()) return SearchResult(emptyList())

        // 应用范围过滤
        val scopedEngines = when (request.scopeMode) {
            SearchScopeMode.SINGLE_SOURCE -> {
                val id = request.selectedSourceUrl ?: return SearchResult(emptyList())
                engines.filter { it.id.toString() == id }
            }
            else -> engines
        }

        if (scopedEngines.isEmpty()) return SearchResult(emptyList())

        // 按分类构建 SourceFieldItem
        val tabs = if (request.selectedTab == "__ALL__") {
            TAB_FIELDS
        } else {
            mapOf(request.selectedTab to (TAB_FIELDS[request.selectedTab] ?: emptyList()))
        }

        val items = mutableListOf<SourceFieldItem>()
        for (engine in scopedEngines) {
            val engineId = engine.id.toString()
            val engineName = engine.name.ifBlank { "未命名($engineId)" }
            for ((tabKey, fields) in tabs) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(engine, fieldKey) ?: continue
                    if (value.isNotBlank()) {
                        items.add(
                            SourceFieldItem(
                                sourceName = engineName,
                                sourceUrl = engineId,
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
            val jsonItems = scopedEngines.mapNotNull { engine ->
                val engineId = engine.id.toString()
                val json = cachedJsonStrings[engineId] ?: return@mapNotNull null
                JsonSearchItem(engine.name.ifBlank { "未命名($engineId)" }, engineId, json)
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
            showDialogFragment(HttpTtsEditDialog(it))
        }
    }

    override fun getTabNames(): Map<String, String> = TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        val engineIds = sourceUrls.mapNotNull { it.toLongOrNull() }
        viewModel.exportEngines(engineIds) { file ->
            activity?.share(file)
        }
    }

    private fun getFieldValue(engine: HttpTTS, fieldKey: String): String? {
        return when (fieldKey) {
            "name" -> engine.name
            "id" -> engine.id.toString()
            "lastUpdateTime" -> engine.lastUpdateTime.toString()
            "url" -> engine.url
            "contentType" -> engine.contentType
            "header" -> engine.header
            "concurrentRate" -> engine.concurrentRate
            "enabledCookieJar" -> engine.enabledCookieJar?.let { if (it) "启用" else "禁用" }
            "loginUrl" -> engine.loginUrl
            "loginUi" -> engine.loginUi
            "loginCheckJs" -> engine.loginCheckJs
            "jsLib" -> engine.jsLib
            else -> null
        }
    }
}
