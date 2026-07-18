package io.legado.app.ui.rss.source.manage

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.source.ContentSearchEngine
import io.legado.app.ui.source.JsonSearchItem
import io.legado.app.ui.source.SearchRequest
import io.legado.app.ui.source.SearchResult
import io.legado.app.ui.source.SearchScopeMode
import io.legado.app.ui.source.SourceBrief
import io.legado.app.ui.source.SourceFieldItem
import io.legado.app.ui.source.SourceMetadata
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RssSourceContentSearchViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        val TAB_NAMES = mapOf(
            "base" to "基础",
            "start" to "启动",
            "list" to "列表",
            "webview" to "正文"
        )

        val TAB_FIELDS = mapOf(
            "base" to listOf(
                "sourceUrl" to "源地址",
                "sourceName" to "源名称",
                "sourceGroup" to "源分组",
                "sourceComment" to "源注释",
                "searchUrl" to "搜索地址",
                "sortUrl" to "分类地址",
                "loginUrl" to "登录地址",
                "loginUi" to "登录界面",
                "loginCheckJs" to "登录检查JS",
                "header" to "请求头",
                "variableComment" to "变量说明",
                "concurrentRate" to "并发率",
                "jsLib" to "jsLib"
            ),
            "start" to listOf(
                "startHtml" to "启动页HTML",
                "startStyle" to "启动页样式",
                "startJs" to "启动页JS",
                "preloadJs" to "预注入JS"
            ),
            "list" to listOf(
                "ruleArticles" to "列表规则",
                "ruleNextPage" to "列表下一页规则",
                "ruleTitle" to "标题规则",
                "rulePubDate" to "时间规则",
                "ruleDescription" to "描述规则",
                "ruleImage" to "图片URL规则",
                "ruleLink" to "链接规则"
            ),
            "webview" to listOf(
                "ruleContent" to "正文规则",
                "style" to "正文样式",
                "injectJs" to "注入JS",
                "shouldOverrideUrlLoading" to "URL拦截",
                "contentWhitelist" to "白名单",
                "contentBlacklist" to "黑名单"
            )
        )
    }

    /** 旧接口保留，供外部调用 */
    suspend fun loadSources(allSources: Boolean): List<RssSource> {
        return withContext(Dispatchers.IO) {
            if (allSources) {
                appDb.rssSourceDao.all
            } else {
                appDb.rssSourceDao.all.filter { it.enabled }
            }
        }
    }

    /**
     * 加载源元信息（源名称+URL列表、分组列表），不加载字段数据。
     */
    suspend fun loadSourceMetadata(enabledOnly: Boolean): SourceMetadata {
        return withContext(Dispatchers.IO) {
            val sources = if (enabledOnly) {
                appDb.rssSourceDao.all.filter { it.enabled }
            } else {
                appDb.rssSourceDao.all
            }
            val briefs = sources.map { SourceBrief(it.sourceName, it.sourceUrl) }
            val groups = appDb.rssSourceDao.allGroups()
            SourceMetadata(briefs, groups)
        }
    }

    /**
     * 执行内容搜索（数据库侧搜索）。
     * 先用 SQL LIKE 过滤出匹配的源，再对匹配源构建字段条目做精细搜索。
     */
    suspend fun searchContent(request: SearchRequest): SearchResult {
        return withContext(Dispatchers.IO) {
            // 1. SQL LIKE 快速过滤出匹配的源
            val matchedSources = if (request.allSources) {
                appDb.rssSourceDao.searchAllFieldsAll(request.query)
            } else {
                appDb.rssSourceDao.searchAllFieldsEnabled(request.query)
            }

            // 2. 应用范围过滤（单源/分组）
            val scopedSources = when (request.scopeMode) {
                SearchScopeMode.SINGLE_SOURCE -> {
                    val url = request.selectedSourceUrl
                        ?: return@withContext SearchResult(emptyList())
                    matchedSources.filter { it.sourceUrl == url }
                }
                SearchScopeMode.GROUP -> {
                    val group = request.selectedSourceGroup
                        ?: return@withContext SearchResult(emptyList())
                    matchedSources.filter {
                        it.sourceGroup?.split(",")?.any { g -> g.trim() == group } == true
                    }
                }
                else -> matchedSources
            }

            if (scopedSources.isEmpty()) return@withContext SearchResult(emptyList())

            // 3. 仅对匹配的源构建 SourceFieldItem
            val sourceItems = buildSourceFieldItems(scopedSources, request.selectedTab)

            // 4. 在匹配的字段条目中做精细搜索
            val results = if (request.searchByRuleField) {
                ContentSearchEngine.searchFields(request.query, sourceItems)
            } else {
                val jsonItems = scopedSources.map { source ->
                    JsonSearchItem(source.sourceName, source.sourceUrl, GSON.toJson(source))
                }
                ContentSearchEngine.searchJson(
                    query = request.query,
                    sourceItems = sourceItems,
                    jsonItems = jsonItems
                )
            }

            SearchResult(results)
        }
    }

    private fun buildSourceFieldItems(
        sources: List<RssSource>,
        selectedTab: String
    ): List<SourceFieldItem> {
        val items = mutableListOf<SourceFieldItem>()
        val tabs = if (selectedTab == "__ALL__") {
            TAB_FIELDS
        } else {
            mapOf(selectedTab to (TAB_FIELDS[selectedTab] ?: emptyList()))
        }
        for (source in sources) {
            for ((tabKey, fields) in tabs) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(source, fieldKey) ?: continue
                    if (value.isNotBlank()) {
                        items.add(
                            SourceFieldItem(
                                sourceName = source.sourceName,
                                sourceUrl = source.sourceUrl,
                                tabKey = tabKey,
                                tabName = TAB_NAMES[tabKey] ?: tabKey,
                                fieldKey = fieldKey,
                                fieldName = fieldName,
                                value = value,
                                sourceGroup = source.sourceGroup
                            )
                        )
                    }
                }
            }
        }
        return items
    }

    private fun getFieldValue(source: RssSource, fieldKey: String): String? {
        return when (fieldKey) {
            "sourceUrl" -> source.sourceUrl
            "sourceName" -> source.sourceName
            "sourceGroup" -> source.sourceGroup
            "sourceComment" -> source.sourceComment
            "searchUrl" -> source.searchUrl
            "sortUrl" -> source.sortUrl
            "loginUrl" -> source.loginUrl
            "loginUi" -> source.loginUi
            "loginCheckJs" -> source.loginCheckJs
            "header" -> source.header
            "variableComment" -> source.variableComment
            "concurrentRate" -> source.concurrentRate
            "jsLib" -> source.jsLib
            "startHtml" -> source.startHtml
            "startStyle" -> source.startStyle
            "startJs" -> source.startJs
            "preloadJs" -> source.preloadJs
            "ruleArticles" -> source.ruleArticles
            "ruleNextPage" -> source.ruleNextPage
            "ruleTitle" -> source.ruleTitle
            "rulePubDate" -> source.rulePubDate
            "ruleDescription" -> source.ruleDescription
            "ruleImage" -> source.ruleImage
            "ruleLink" -> source.ruleLink
            "ruleContent" -> source.ruleContent
            "style" -> source.style
            "injectJs" -> source.injectJs
            "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading
            "contentWhitelist" -> source.contentWhitelist
            "contentBlacklist" -> source.contentBlacklist
            else -> null
        }
    }

    fun loadSources(allSources: Boolean, callback: (List<RssSource>) -> Unit) {
        execute {
            val sources = if (allSources) {
                appDb.rssSourceDao.all
            } else {
                appDb.rssSourceDao.all.filter { it.enabled }
            }
            sources
        }.onSuccess {
            callback(it ?: emptyList())
        }.onError {
            callback(emptyList())
        }
    }

    fun exportSources(sourceUrls: List<String>, success: (File) -> Unit) {
        execute {
            val sources = appDb.rssSourceDao.all.filter {
                it.sourceUrl in sourceUrls
            }
            val path = "${context.filesDir}/shareRssSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use { out ->
                GSON.writeToOutputStream(out, sources)
            }
            file
        }.onSuccess {
            if (it != null) success(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }
}
