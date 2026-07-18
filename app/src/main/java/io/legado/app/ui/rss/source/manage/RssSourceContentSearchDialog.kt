package io.legado.app.ui.rss.source.manage

import androidx.fragment.app.viewModels
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.source.BaseContentSearchDialog
import io.legado.app.ui.source.ContentSearchType
import io.legado.app.ui.source.SearchRequest
import io.legado.app.ui.source.SearchResult
import io.legado.app.ui.source.SourceMetadata
import io.legado.app.utils.share
import io.legado.app.utils.startActivity

/**
 * 订阅源内容查询界面，用于按规则字段或完整 JSON 搜索订阅源配置。
 * 使用数据库侧搜索，避免全量加载到内存。
 */
class RssSourceContentSearchDialog : BaseContentSearchDialog() {

    private val viewModel by viewModels<RssSourceContentSearchViewModel>()

    override fun getDialogTitle() = "订阅源内容查询"

    override fun getSearchHint() = "输入关键词搜索选择的订阅源"

    override fun getContentSearchType() = ContentSearchType.RSS_SOURCE

    override suspend fun loadSourceMetadata(allSources: Boolean): SourceMetadata {
        return viewModel.loadSourceMetadata(!allSources)
    }

    override suspend fun searchContent(request: SearchRequest): SearchResult {
        return viewModel.searchContent(request)
    }

    override fun navigateToEdit(sourceUrl: String, tabKey: String?, fieldKey: String?) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
            if (!tabKey.isNullOrBlank()) {
                putExtra("tabKey", tabKey)
            }
            if (!fieldKey.isNullOrBlank()) {
                putExtra("fieldKey", fieldKey)
            }
        }
    }

    override fun getTabNames(): Map<String, String> = RssSourceContentSearchViewModel.TAB_NAMES

    override fun exportSources(sourceUrls: List<String>) {
        viewModel.exportSources(sourceUrls) { file ->
            activity?.share(file)
        }
    }
}
