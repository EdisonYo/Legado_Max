package io.legado.app.ui.book.explore

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.blockrule.BlockRule
import io.legado.app.model.blockrule.BlockRuleStore
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreShowViewModel(application: Application) : BaseViewModel(application) {
    companion object {
        private val pageQueryRegex = Regex("""([?&]page=)(\d+)""", RegexOption.IGNORE_CASE)
    }

    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val upAdapterLiveData = MutableLiveData<String>()
    val booksData = MutableLiveData<List<SearchBook>>()
    val addBooksData = MutableLiveData<List<SearchBook>>()
    val errorLiveData = MutableLiveData<String>()
    val errorTopLiveData = MutableLiveData<String>()
    val pageLiveData = MutableLiveData<Int>()
    val addAllToShelfResult = MutableLiveData<Int>()
    /** 屏蔽规则变化后通知UI全量刷新书籍列表 */
    val blockRulesRefreshData = MutableLiveData<List<SearchBook>>()
    /** 屏蔽数量变化通知UI更新进度指示器 */
    val blockedCountData = MutableLiveData<Int>()
    /** 实际匹配到书籍的规则列表，用于"起效的规则"展示 */
    val matchedRulesData = MutableLiveData<List<BlockRule>>()
    val booksCount: Int get() = books.size
    /** 所有发现分类列表，用于Tab显示 */
    val exploreKindsData = MutableLiveData<List<ExploreKind>>()
    private var bookSource: BookSource? = null
    private var exploreUrl: String? = null
    private var page = 1
    private var books = linkedSetOf<SearchBook>()
    /** 原始未过滤的书籍列表，用于屏蔽规则变化时重新过滤 */
    private var allBooks = linkedSetOf<SearchBook>()
    /** 获取原始未过滤书籍列表的副本 */
    val allBooksList: List<SearchBook> get() = allBooks.toList()
    /** 当前书源URL，用于屏蔽规则过滤 */
    var currentSourceUrl: String = ""

    /** 分类数据缓存（分类URL -> 缓存数据），跨配置变更保留 */
    private data class CategoryCache(
        val rawBooks: List<SearchBook>,
        val page: Int,
        val hasMore: Boolean
    )
    private val categoryDataCache = mutableMapOf<String, CategoryCache>()

    //实时监听数据库对比书名作者，判断书是否在书架上
    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add("${it.name}-${it.author}")
                        keys.add(it.name)
                        keys.add(it.bookUrl)
                    }
                keys
            }.catch {
                AppLog.put("发现列表界面获取书籍数据失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    /**
     * ViewModel初始化数据
     */
    fun initData(intent: Intent) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            currentSourceUrl = sourceUrl ?: ""
            exploreUrl = intent.getStringExtra("exploreUrl")
            page = parsePageFromUrl(exploreUrl)
            if (bookSource == null && sourceUrl != null) {
                bookSource = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            pageLiveData.postValue(page)
            loadExploreKinds()
            explore()
        }
    }

    /**
     * 加载书源的所有发现分类
     */
    private suspend fun loadExploreKinds() {
        val source = bookSource
        if (source == null) {
            exploreKindsData.postValue(emptyList())
            return
        }
        withContext(IO) {
            kotlin.runCatching {
                source.exploreKinds().filter { !it.url.isNullOrBlank() }
            }.onSuccess { kinds ->
                exploreKindsData.postValue(kinds)
            }.onFailure {
                exploreKindsData.postValue(emptyList())
            }
        }
    }

    /**
     * 切换到指定分类
     * 清空当前书籍列表，加载新分类的书籍
     *
     * @param newUrl 分类URL
     * @param exploreName 分类名称（用于标题栏）
     */
    fun switchCategory(newUrl: String, exploreName: String? = null) {
        execute {
            books.clear()
            allBooks.clear()
            page = parsePageFromUrl(newUrl)
            exploreUrl = newUrl
            pageLiveData.postValue(page)
            explore()
        }
    }

    /**
     * 检查指定分类是否有缓存数据
     */
    fun hasCategoryCache(url: String): Boolean {
        return categoryDataCache.containsKey(url)
    }

    /**
     * 保存当前分类的数据缓存
     */
    fun saveCategoryCache(url: String) {
        if (allBooks.isEmpty()) return
        categoryDataCache[url] = CategoryCache(
            rawBooks = allBooks.toList(),
            page = page,
            hasMore = true // 简化处理，实际可根据loadMoreView状态判断
        )
    }

    /**
     * 恢复指定分类的缓存数据
     * 使用当前屏蔽规则重新过滤，保证数据实时性
     */
    fun restoreCategory(url: String) {
        execute {
            val cache = categoryDataCache[url] ?: return@execute
            books.clear()
            allBooks.clear()
            allBooks.addAll(cache.rawBooks)
            page = cache.page
            exploreUrl = url
            pageLiveData.postValue(page)

            val filtered = BlockRuleStore.filterBooks(getApplication(), cache.rawBooks, currentSourceUrl)
            books.addAll(filtered)
            booksData.postValue(books.toList())
            blockedCountData.postValue(allBooks.size - books.size)
        }
    }

    /**
     * 上滑触发的增量更新
     */
    fun explore(page: Int) {
        val source = bookSource
        val url = buildExploreUrl(page)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, page)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, currentSourceUrl)
                val newBooks = linkedSetOf<SearchBook>()
                newBooks.addAll(filtered)
                newBooks.addAll(books)
                books = newBooks
                addBooksData.postValue(filtered)
                blockedCountData.postValue(allBooks.size - books.size)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(page)
            }.onError {
                it.printOnDebug()
                errorTopLiveData.postValue(it.stackTraceStr)
            }
    }

    /**
     * 跳转到指定页码
     */
    fun skipPage(page: Int) {
        if (page > 0) {
            books.clear()
            allBooks.clear()
            this.page = page
            pageLiveData.postValue(page)
        }
    }
    /**
     * 网络请求核心逻辑
     */
    fun explore() {
        val source = bookSource
        val requestPage = page
        val url = buildExploreUrl(requestPage)
        if (source == null || url == null) return
        WebBook.exploreBook(viewModelScope, source, url, requestPage)
            .timeout(if (BuildConfig.DEBUG) 0L else 60000L)
            .onSuccess(IO) { searchBooks ->
                allBooks.addAll(searchBooks)
                val filtered = BlockRuleStore.filterBooks(getApplication(), searchBooks, currentSourceUrl)
                books.addAll(filtered)
                booksData.postValue(books.toList())
                blockedCountData.postValue(allBooks.size - books.size)
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
                pageLiveData.postValue(requestPage)
                page = requestPage + 1
            }.onError {
                it.printOnDebug()
                errorLiveData.postValue(it.stackTraceStr)
            }
    }

    private fun parsePageFromUrl(url: String?): Int {
        val pageValue = url?.let {
            // 从URL中提取页码，分页机制
            // 例如：https://www.baidu.com/explore?page=2
            pageQueryRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull()
        }
        return pageValue?.takeIf { it > 0 } ?: 1
    }

    private fun buildExploreUrl(page: Int): String? {
        val safePage = page.coerceAtLeast(1)
        val currentUrl = exploreUrl ?: return null
        val updatedUrl = pageQueryRegex.replace(currentUrl) {
            "${it.groupValues[1]}$safePage"
        }
        exploreUrl = updatedUrl
        return updatedUrl
    }

    /**
     * 屏蔽规则变化后重新过滤当前书籍列表
     */
    fun applyBlockRules(sourceUrl: String) {
        currentSourceUrl = sourceUrl
        BlockRuleStore.invalidateCache()
        val matched = BlockRuleStore.getMatchedRules(getApplication(), allBooks.toList(), sourceUrl)
        val filtered = BlockRuleStore.filterBooks(getApplication(), allBooks.toList(), sourceUrl)
        books = linkedSetOf<SearchBook>().apply { addAll(filtered) }
        blockedCountData.postValue(allBooks.size - books.size)
        matchedRulesData.postValue(matched)
        blockRulesRefreshData.postValue(books.toList())
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

    fun addAllToShelf(groupId: Long) {
        execute {
            val booksToAdd = books.filterNot { isInBookShelf(it) }
            if (booksToAdd.isEmpty()) {
                addAllToShelfResult.postValue(0)
                return@execute
            }

            val bookEntities = booksToAdd.mapIndexed { index, searchBook ->
                searchBook.toBook().apply {
                    this.group = groupId
                    this.order = index
                }
            }

            appDb.bookDao.insert(*bookEntities.toTypedArray())

            bookEntities.forEach { book ->
                val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
                bookshelf.add(key)
                bookshelf.add(book.bookUrl)
            }

            addAllToShelfResult.postValue(booksToAdd.size)
        }.onError {
            AppLog.put("批量加入书架失败", it)
            errorLiveData.postValue("批量加入书架失败: ${it.localizedMessage}")
        }
    }

}