package io.legado.app.ui.book.explore

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.ActivityExploreShowBinding
import io.legado.app.databinding.ViewLoadMoreBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.blockrule.BlockRuleConfigDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.LoadMoreView
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 发现列表
 */
class ExploreShowActivity : VMBaseActivity<ActivityExploreShowBinding, ExploreShowViewModel>(),
    ExploreShowAdapter.CallBack, GroupSelectDialog.CallBack {

    companion object {
        private const val REQUEST_CODE_ADD_ALL_TO_SHELF = 1001
        /** 加载下一页的冷却间隔（毫秒），滚动过快时隔 2 秒再请求 */
        private const val LOAD_COOLDOWN_MS = 2000L
        const val LAYOUT_LIST = 0
        const val LAYOUT_GRID = 1
        const val LAYOUT_WATERFALL = 2
    }

    override val binding by viewBinding(ActivityExploreShowBinding::inflate)
    override val viewModel by viewModels<ExploreShowViewModel>()

    private val adapter by lazy { ExploreShowAdapter(this, this) }
    private val loadMoreView by lazy { LoadMoreView(this) }
    private val loadMoreViewTop by lazy { LoadMoreView(this) }
    private var oldPage = -1
    private var isClearAll = false
    private var menuPage: MenuItem? = null
    private var menuSwitchLayout: MenuItem? = null
    private var menuSelectColumn: MenuItem? = null
    private var menuShowCategoryTab: MenuItem? = null
    /** 当前书源 URL，用于按书源隔离布局配置 */
    private val sourceUrl: String by lazy { intent.getStringExtra("sourceUrl") ?: "" }
    /** 是否显示分类Tab，按书源持久化，默认 true */
    private var showCategoryTab: Boolean
        get() = getPrefBoolean("${PreferKey.exploreShowCategoryTab}_${sourceUrl}", true)
        set(value) = putPrefBoolean("${PreferKey.exploreShowCategoryTab}_${sourceUrl}", value)
    /** 是否显示屏蔽进度指示器 */
    private var showBlockProgress: Boolean
        get() = getPrefBoolean(PreferKey.blockRuleShowProgress, false)
        set(value) = putPrefBoolean(PreferKey.blockRuleShowProgress, value)
    /** 当前被屏蔽的书籍数量，用于进度指示器 */
    private var blockedCount by mutableIntStateOf(0)
    /** 屏蔽进度悬浮芯片 ComposeView */
    private var blockProgressComposeView: ComposeView? = null
    /** 上次发起加载下一页的时间戳，用于 2 秒冷却限制 */
    private var lastLoadTime = 0L

    /** 冷却期延迟重试的 Handler */
    private val handler = Handler(Looper.getMainLooper())

    /** 是否已有延迟重试排队中 */
    private var loadRetryScheduled = false

    /** 网格模式列数，按书源持久化，默认 2 */
    private var columnCountGrid: Int
        get() = getPrefInt("${PreferKey.exploreShowColumn}_${sourceUrl}", 2)
        set(value) = putPrefInt("${PreferKey.exploreShowColumn}_${sourceUrl}", value)

    /** 瀑布流模式列数，按书源持久化，默认 2 */
    private var columnCountWaterfall: Int
        get() = getPrefInt("${PreferKey.exploreShowColumnWaterfall}_${sourceUrl}", 2)
        set(value) = putPrefInt("${PreferKey.exploreShowColumnWaterfall}_${sourceUrl}", value)

    /** 分类列表 */
    private val exploreKinds = mutableListOf<ExploreKind>()
    /** 当前选中的分类索引 */
    private var currentCategoryIndex = 0
    /** 每个分类的滚动位置缓存（分类URL -> 滚动位置和偏移量） */
    private data class ScrollState(val position: Int, val offset: Int)
    private val categoryScrollCache = mutableMapOf<String, ScrollState>()

    /**
     * 布局模式，由"切换布局"菜单轮换，按书源持久化
     * 0=列表, 1=网格, 2=瀑布流
     */
    private var layoutMode: Int
        get() = getPrefInt("${PreferKey.exploreGridMode}_${sourceUrl}", 0)
        set(value) = putPrefInt("${PreferKey.exploreGridMode}_${sourceUrl}", value)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initRecyclerView()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.addBooksData.observe(this) { upDataTop(it) }
        viewModel.blockRulesRefreshData.observe(this) { refreshDataAfterBlock(it) }
        viewModel.blockedCountData.observe(this) { count ->
            blockedCount = count
            updateBlockProgressChip()
        }
        // 分类列表：超过1个且开启显示时才显示Tab
        viewModel.exploreKindsData.observe(this) { kinds ->
            exploreKinds.clear()
            exploreKinds.addAll(kinds)
            if (showCategoryTab && exploreKinds.size > 1) {
                setupTabs()
                binding.tabsContainer.visibility = View.VISIBLE
                val currentUrl = intent.getStringExtra("exploreUrl") ?: ""
                currentCategoryIndex = exploreKinds.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
                updateTabSelection(currentCategoryIndex)
            } else {
                binding.tabsContainer.visibility = View.GONE
            }
        }
        viewModel.initData(intent)
        viewModel.errorLiveData.observe(this) {
            loadMoreView.error(it)
        }
        viewModel.errorTopLiveData.observe(this) {
            loadMoreViewTop.error(it)
        }
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }
        viewModel.pageLiveData.observe(this) {
            menuPage?.title = getString(R.string.menu_page, it)
        }
        viewModel.addAllToShelfResult.observe(this) { count ->
            if (count == 0) {
                toastOnUi(R.string.all_books_in_shelf)
            } else {
                toastOnUi(getString(R.string.add_books_success, count))
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explore_show, menu)
        menuPage = menu.findItem(R.id.menu_page)
        menuSwitchLayout = menu.findItem(R.id.menu_switch_layout)
        menuSelectColumn = menu.findItem(R.id.menu_select_column)
        menuShowCategoryTab = menu.findItem(R.id.menu_show_category_tab)
        menuShowCategoryTab?.isChecked = showCategoryTab
        if (layoutMode != LAYOUT_LIST) {
            menuSelectColumn?.isVisible = true
            val count = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
            updateColumnMenuTitle()
            adapter.layoutMode = layoutMode
            adapter.columnCount = count
            applyLayoutManager(count)
        }
        updateSwitchLayoutTitle()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        updateSwitchLayoutTitle()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_page -> {
                val page = viewModel.pageLiveData.value ?: 1
                NumberPickerDialog(this)
                    .setTitle(getString(R.string.change_page))
                    .setMaxValue(999)
                    .setMinValue(1)
                    .setValue(page)
                    .show {
                        if (page != it) {
                            if (oldPage == -1 && it != 1) {
                                adapter.addHeaderView {
                                    ViewLoadMoreBinding.bind(loadMoreViewTop)
                                }
                            } else if (it != 1) {
                                val layoutParams = loadMoreViewTop.layoutParams
                                if (layoutParams?.height == 0) {
                                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    loadMoreViewTop.layoutParams = layoutParams
                                }
                            }
                            oldPage = it
                            viewModel.skipPage(it)
                            isClearAll = true
                            adapter.clearItems()
                            if (!loadMoreView.hasMore) {
                                scrollToBottom(true)
                            }
                        }
                    }
            }
            R.id.menu_add_all_to_shelf -> {
                showDialogFragment(GroupSelectDialog(0, REQUEST_CODE_ADD_ALL_TO_SHELF))
            }
            R.id.menu_switch_layout -> {
                handleSwitchLayout()
            }
            R.id.menu_block_rule -> {
                showBlockRuleConfig()
            }
            R.id.menu_select_column -> {
                handleSelectColumn()
            }
            R.id.menu_show_category_tab -> {
                showCategoryTab = !showCategoryTab
                item.isChecked = showCategoryTab
                if (showCategoryTab && exploreKinds.size > 1) {
                    if (binding.tabsContainer.childCount == 0 && exploreKinds.isNotEmpty()) {
                        setupTabs()
                    }
                    binding.tabsContainer.visibility = View.VISIBLE
                } else {
                    binding.tabsContainer.visibility = View.GONE
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 打开屏蔽规则配置弹窗
     */
    private fun showBlockRuleConfig() {
        val dialog = BlockRuleConfigDialog()
        dialog.sourceUrl = sourceUrl
        dialog.allBooks = viewModel.allBooksList
        dialog.onRulesChanged = {
            viewModel.applyBlockRules(sourceUrl)
        }
        dialog.onShowProgressChanged = {
            showBlockProgress = it
            updateBlockProgressChip()
        }
        dialog.show(supportFragmentManager, "exploreBlockRuleConfig")
    }

    /**
     * 更新屏蔽进度悬浮芯片的显示状态
     * 芯片位于列表上方右侧，点击可打开屏蔽规则配置
     */
    private fun updateBlockProgressChip() {
        val contentView = binding.contentView
        if (showBlockProgress && blockedCount > 0) {
            if (blockProgressComposeView == null) {
                blockProgressComposeView = ComposeView(this).also { composeView ->
                    composeView.setContent {
                        LegadoTheme {
                            Surface(
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shadowElevation = 4.dp,
                                onClick = { showBlockRuleConfig() }
                            ) {
                                Text(
                                    text = getString(R.string.explore_block_rule_progress_text, blockedCount),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    val params = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    }
                    contentView.addView(composeView, params)
                }
            }
        } else {
            blockProgressComposeView?.let {
                contentView.removeView(it)
                blockProgressComposeView = null
            }
        }
    }

    /**
     * 设置分类Tab布局（FlexboxLayout 自动换行）
     * 圆角16dp，未选中淡描边，选中实描边加粗，文字始终 primaryText
     */
    private fun setupTabs() {
        val container = binding.tabsContainer
        container.removeAllViews()
        if (exploreKinds.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        val accent = accentColor
        val textColor = ContextCompat.getColor(this, R.color.primaryText)

        exploreKinds.forEachIndexed { index, kind ->
            val tab = createTabView(kind.title ?: "", index, kind.url ?: "", accent, textColor)
            container.addView(tab)
        }

        container.visibility = View.VISIBLE
        updateTabSelection(currentCategoryIndex)
    }

    /**
     * 创建单个Tab视图
     */
    private fun createTabView(title: String, position: Int, url: String, accent: Int, textColor: Int): TextView {
        return TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
            val params = FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 4.dpToPx(), 6.dpToPx(), 4.dpToPx())
            }
            layoutParams = params
            background = createTabBackground(accent)
            setTextColor(textColor)
            setOnClickListener {
                if (position != currentCategoryIndex) {
                    switchToCategory(position)
                }
            }
        }
    }

    /**
     * 创建Tab背景
     * 未选中：透明底 + accent 40%透明度 1dp 描边
     * 选中：透明底 + accent 实色 2dp 描边
     * 圆角 16dp
     */
    private fun createTabBackground(accent: Int): android.graphics.drawable.Drawable {
        val radius = 16f.dpToPx()
        val strokeWidthNormal = 1.dpToPx()
        val strokeWidthSelected = 2.dpToPx()
        val fadedAccent = Color.argb(
            (255 * 0.4).toInt(),
            Color.red(accent),
            Color.green(accent),
            Color.blue(accent)
        )

        val defaultDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(strokeWidthNormal, fadedAccent)
            setColor(Color.TRANSPARENT)
        }

        val selectedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(strokeWidthSelected, accent)
            setColor(Color.TRANSPARENT)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), selectedDrawable)
            addState(intArrayOf(), defaultDrawable)
        }
    }

    /**
     * 切换到指定分类
     */
    private fun switchToCategory(index: Int) {
        val targetKind = exploreKinds.getOrNull(index) ?: return
        val targetUrl = targetKind.url ?: return
        val currentKind = exploreKinds.getOrNull(currentCategoryIndex)
        val currentUrl = currentKind?.url

        // 1. 保存当前分类状态
        if (currentUrl != null) {
            saveCurrentScrollPosition(currentUrl)
            viewModel.saveCategoryCache(currentUrl)
        }

        // 2. 更新选中状态
        currentCategoryIndex = index
        updateTabSelection(index)

        // 3. 清空当前视图
        adapter.clearItems()
        loadMoreView.hasMore()
        loadMoreView.startLoad()
        binding.titleBar.title = targetKind.title

        // 4. 检查目标分类是否有缓存
        if (viewModel.hasCategoryCache(targetUrl)) {
            // 有缓存：恢复数据，不发起网络请求
            viewModel.restoreCategory(targetUrl)
        } else {
            // 无缓存：正常加载
            viewModel.switchCategory(targetUrl, targetKind.title)
        }
    }

    /**
     * 更新Tab选中状态
     */
    private fun updateTabSelection(position: Int) {
        val container = binding.tabsContainer
        for (i in 0 until container.childCount) {
            val tab = container.getChildAt(i) as? TextView ?: continue
            tab.isSelected = i == position
        }
    }

    /**
     * 保存当前分类的滚动位置
     */
    private fun saveCurrentScrollPosition(url: String) {
        val lm = binding.recyclerView.layoutManager ?: return
        val position = when (lm) {
            is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
            is StaggeredGridLayoutManager -> {
                val positions = IntArray(lm.spanCount)
                lm.findFirstVisibleItemPositions(positions)
                positions.minOrNull() ?: 0
            }
            else -> 0
        }
        val firstView = lm.findViewByPosition(position)
        val offset = firstView?.top?.minus(binding.recyclerView.paddingTop) ?: 0
        categoryScrollCache[url] = ScrollState(position, offset)
    }

    /**
     * 恢复指定分类的滚动位置
     */
    private fun restoreCategoryScrollPosition(url: String?) {
        if (url == null) {
            binding.recyclerView.scrollToPosition(0)
            return
        }
        val state = categoryScrollCache[url]
        if (state == null) {
            binding.recyclerView.scrollToPosition(0)
            return
        }
        val lm = binding.recyclerView.layoutManager
        when (lm) {
            is LinearLayoutManager -> lm.scrollToPositionWithOffset(state.position, state.offset)
            is StaggeredGridLayoutManager -> lm.scrollToPositionWithOffset(state.position, state.offset)
            else -> binding.recyclerView.scrollToPosition(state.position)
        }
    }

    /**
     * 切换布局：列表 → 网格 → 瀑布流 轮换
     */
    private fun handleSwitchLayout() {
        val savedPosition = findFirstVisibleItemPosition()
        layoutMode = (layoutMode + 1) % 3
        when (layoutMode) {
            LAYOUT_LIST -> {
                menuSelectColumn?.isVisible = false
                adapter.layoutMode = LAYOUT_LIST
                applyLayoutManager(1)
            }
            LAYOUT_GRID -> {
                menuSelectColumn?.isVisible = true
                if (columnCountGrid < 1 || columnCountGrid > 10) {
                    columnCountGrid = 2
                }
                updateColumnMenuTitle()
                adapter.layoutMode = layoutMode
                adapter.columnCount = columnCountGrid
                applyLayoutManager(columnCountGrid)
            }
            LAYOUT_WATERFALL -> {
                menuSelectColumn?.isVisible = true
                if (columnCountWaterfall < 1 || columnCountWaterfall > 10) {
                    columnCountWaterfall = 2
                }
                updateColumnMenuTitle()
                adapter.layoutMode = layoutMode
                adapter.columnCount = columnCountWaterfall
                applyLayoutManager(columnCountWaterfall)
            }
        }
        restoreScrollPosition(savedPosition)
        updateSwitchLayoutTitle()
    }

    /**
     * 弹出 NumberPickerDialog 选择列数（1-10）
     */
    private fun handleSelectColumn() {
        val currentCount = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        val savedPosition = findFirstVisibleItemPosition()
        NumberPickerDialog(this)
            .setTitle(getString(R.string.select_column_count))
            .setMaxValue(10)
            .setMinValue(1)
            .setValue(currentCount)
            .show { selectedCount ->
                if (layoutMode == LAYOUT_WATERFALL) {
                    columnCountWaterfall = selectedCount
                } else {
                    columnCountGrid = selectedCount
                }
                updateColumnMenuTitle()
                adapter.columnCount = selectedCount
                applyLayoutManager(selectedCount)
                restoreScrollPosition(savedPosition)
            }
    }

    /**
     * 根据列数和当前布局模式设置 RecyclerView 的 LayoutManager
     */
    private fun applyLayoutManager(count: Int) {
        binding.recyclerView.layoutManager = when {
            layoutMode == LAYOUT_LIST || count <= 1 -> {
                binding.recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
                LinearLayoutManager(this)
            }
            layoutMode == LAYOUT_WATERFALL -> {
                binding.recyclerView.itemAnimator = null
                StaggeredGridLayoutManager(count, StaggeredGridLayoutManager.VERTICAL)
            }
            else -> {
                binding.recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
                GridLayoutManager(this, count)
            }
        }
    }

    /**
     * 获取当前 LayoutManager 中第一个可见项的位置
     */
    private fun findFirstVisibleItemPosition(): Int {
        val layoutManager = binding.recyclerView.layoutManager ?: return 0
        return when (layoutManager) {
            is StaggeredGridLayoutManager -> {
                val positions = IntArray(layoutManager.spanCount)
                layoutManager.findFirstVisibleItemPositions(positions)
                positions.minOrNull() ?: 0
            }
            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            else -> 0
        }
    }

    /**
     * 切换布局/列数后将 RecyclerView 滚动到之前的位置
     */
    private fun restoreScrollPosition(position: Int) {
        if (position < 0) return
        val layoutManager = binding.recyclerView.layoutManager ?: return
        if (position >= adapter.itemCount) return
        layoutManager.scrollToPosition(position)
    }

    /**
     * 更新标题栏中选择分列菜单项的标题为当前布局模式对应的列数值
     */
    private fun updateColumnMenuTitle() {
        val count = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        menuSelectColumn?.title = count.toString()
    }

    /**
     * 更新切换布局菜单标题为当前模式名称
     */
    private fun updateSwitchLayoutTitle() {
        val modeName = when (layoutMode) {
            LAYOUT_GRID -> getString(R.string.switch_layout_grid)
            LAYOUT_WATERFALL -> getString(R.string.switch_layout_waterfall)
            else -> getString(R.string.switch_layout_list)
        }
        menuSwitchLayout?.title = "${getString(R.string.switch_layout)}(当前:$modeName)"
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        adapter.addFooterView {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        loadMoreView.startLoad()
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading) {
                scrollToBottom(true)
            }
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val kind = exploreKinds.getOrNull(currentCategoryIndex)
                    kind?.url?.let { saveCurrentScrollPosition(it) }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                } else if (!recyclerView.canScrollVertically(-1) && dy < 0) {
                    scrollToTop()
                }
            }
        })
    }

    /**
     * 滚动到底部加载更多，非列表模式下列数 >3 时内置 2 秒冷却限制。
     * 冷却期内延迟重试，避免停在底部无法触发 onScrolled 导致加载卡死。
     */
    private fun scrollToBottom(forceLoad: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val currentCount = if (layoutMode == LAYOUT_WATERFALL) columnCountWaterfall else columnCountGrid
        if (layoutMode != LAYOUT_LIST && currentCount > 3 && now - lastLoadTime < LOAD_COOLDOWN_MS) {
            scheduleLoadRetry(LOAD_COOLDOWN_MS - (now - lastLoadTime))
            return
        }
        if ((loadMoreView.hasMore && !loadMoreView.isLoading && !loadMoreViewTop.isLoading) || forceLoad) {
            loadRetryScheduled = false
            lastLoadTime = now
            loadMoreView.hasMore()
            viewModel.explore()
        }
    }

    /**
     * 冷却期内延迟重试加载下一页，仅排队一次
     */
    private fun scheduleLoadRetry(delayMs: Long) {
        if (loadRetryScheduled) return
        loadRetryScheduled = true
        handler.postDelayed({
            loadRetryScheduled = false
            scrollToBottom()
        }, delayMs)
    }

    /**
     * 上滑加载上一页
     */
    private fun scrollToTop(forceLoad: Boolean = false) {
        if ((oldPage > 1 && !loadMoreView.isLoading && !loadMoreViewTop.isLoading) || forceLoad) {
            loadMoreViewTop.hasMore()
            oldPage--
            viewModel.explore(oldPage)
        }
    }

    private fun upData(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        if (books.isEmpty() && adapter.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else if (adapter.getActualItemCount() == books.size) {
            loadMoreView.noMore()
        } else {
            // 增量追加新数据，避免 setItems 的 notifyDataSetChanged 导致已渲染封面闪烁
            val oldCount = adapter.getActualItemCount()
            if (oldCount == 0) {
                adapter.setItems(books)
                // 数据加载完成后，恢复当前分类的滚动位置
                val currentKind = exploreKinds.getOrNull(currentCategoryIndex)
                binding.recyclerView.post {
                    restoreCategoryScrollPosition(currentKind?.url)
                }
            } else {
                val newItems = books.subList(oldCount, books.size)
                adapter.addItems(newItems)
            }
            if (isClearAll) {
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(1, 0)
                isClearAll = false
            }
        }
    }

    /**
     * 屏蔽规则变化后全量刷新列表，避免 subList 越界
     */
    private fun refreshDataAfterBlock(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        if (books.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else {
            adapter.setItems(books)
        }
    }

    private fun upDataTop(books: List<SearchBook>) {
        loadMoreViewTop.stopLoad()
        adapter.addItems(0, books)
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() <= 1) {
            layoutManager.scrollToPositionWithOffset(books.size, 0)
        }
        if (oldPage <= 1) {
            val layoutParams = loadMoreViewTop.layoutParams
            if (layoutParams != null) {
                layoutParams.height = 0
                loadMoreViewTop.layoutParams = layoutParams
            }
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    override fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        if (requestCode == REQUEST_CODE_ADD_ALL_TO_SHELF) {
            toastOnUi(getString(R.string.adding_books, viewModel.booksCount))
            viewModel.addAllToShelf(groupId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        categoryScrollCache.clear()
    }
}