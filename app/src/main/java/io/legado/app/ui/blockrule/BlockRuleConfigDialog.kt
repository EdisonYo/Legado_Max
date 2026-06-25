package io.legado.app.ui.blockrule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor

import io.legado.app.model.blockrule.BlockRule
import io.legado.app.model.blockrule.BlockRuleGroupStore
import io.legado.app.model.blockrule.BlockRuleStore
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 屏蔽规则配置弹窗
 * 简约样式、紧凑布局、直角（0dp圆角）
 */
class BlockRuleConfigDialog : DialogFragment() {

    var sourceUrl: String = ""
    var allBooks: List<SearchBook> = emptyList()
    var allRssArticles: List<RssArticle> = emptyList()
    var onRulesChanged: (() -> Unit)? = null
    var onShowProgressChanged: ((Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    BlockRuleConfigContent(
                        sourceUrl = sourceUrl,
                        allBooks = allBooks,
                        allRssArticles = allRssArticles,
                        onDismiss = { dismissAllowingStateLoss() },
                        onRulesChanged = {
                            onRulesChanged?.invoke()
                        },
                        onShowProgressChanged = { show ->
                            onShowProgressChanged?.invoke(show)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockRuleConfigContent(
    sourceUrl: String,
    allBooks: List<SearchBook>,
    allRssArticles: List<RssArticle>,
    onDismiss: () -> Unit,
    onRulesChanged: () -> Unit,
    onShowProgressChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf<List<BlockRule>>(BlockRuleStore.load(context)) }
    var currentGroup by remember { mutableStateOf<String?>(null) }
    var editingRule by remember { mutableStateOf<BlockRule?>(null) }
    var deletingRule by remember { mutableStateOf<BlockRule?>(null) }
    var showGroupManage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(context.getPrefBoolean(PreferKey.blockRuleShowProgress, false)) }
    var masterEnabled by remember { mutableStateOf(context.getPrefBoolean(PreferKey.blockRuleEnabled, true)) }
    var showActiveRules by remember { mutableStateOf(false) }
    var allSources by remember { mutableStateOf<List<BookSource>>(emptyList()) }
    var allRssSources by remember { mutableStateOf<List<RssSource>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allSources = appDb.bookSourceDao.getAllSources()
            allRssSources = appDb.rssSourceDao.all
        }
    }

    fun refresh() {
        BlockRuleStore.invalidateCache()
        rules = BlockRuleStore.load(context)
        onRulesChanged()
    }

    fun saveRules(newRules: List<BlockRule>) {
        BlockRuleStore.save(context, newRules)
        rules = newRules
        onRulesChanged()
    }

    val currentMatchedRules = if (allRssArticles.isNotEmpty()) {
        BlockRuleStore.getMatchedRssRules(context, allRssArticles, sourceUrl)
    } else {
        BlockRuleStore.getMatchedRules(context, allBooks, sourceUrl)
    }
    val filteredRules = when (currentGroup) {
        null -> rules
        BlockRuleGroupStore.BOOK_SOURCE_GROUP -> rules.filter { BlockRuleGroupStore.isInBookSourceGroup(it) }
        BlockRuleGroupStore.RSS_SOURCE_GROUP -> rules.filter { BlockRuleGroupStore.isInRssSourceGroup(it) }
        else -> rules.filter { it.group == currentGroup }
    }
    val groups = BlockRuleGroupStore.load(context)

    // 删除确认 - 小尺寸、无标题、左对齐、横排
    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = null,
            text = {
                Text(
                    text = stringResource(R.string.explore_block_rule_delete_confirm, rule.name.ifBlank { rule.pattern }),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveRules(rules.filterNot { it.id == rule.id })
                        deletingRule = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    // 编辑
    editingRule?.let { rule ->
        BlockRuleEditContent(
            sourceRule = rule,
            groups = groups,
            onSave = { newRule ->
                val index = rules.indexOfFirst { it.id == newRule.id }
                val newRules = if (index >= 0) {
                    rules.toMutableList().also { it[index] = newRule }
                } else {
                    rules + newRule
                }
                saveRules(newRules)
                editingRule = null
            },
            onDismiss = { editingRule = null }
        )
    }

    // 分组管理
    if (showGroupManage) {
        val allGroupsForManage = remember(groups) {
            BlockRuleGroupStore.RESERVED_GROUPS.toList() + groups.filter { it !in BlockRuleGroupStore.RESERVED_GROUPS }
        }
        BlockRuleGroupManageContent(
            groups = allGroupsForManage,
            onAddGroup = { name ->
                val newGroups = (groups + name).distinct()
                BlockRuleGroupStore.save(context, newGroups)
                refresh()
            },
            onRenameGroup = { oldName, newName ->
                val newRules = rules.map {
                    if (it.group == oldName) it.copy(group = newName) else it
                }
                val newGroups = groups.map { if (it == oldName) newName else it }
                BlockRuleGroupStore.save(context, newGroups)
                saveRules(newRules)
                if (currentGroup == oldName) currentGroup = newName
            },
            onDeleteGroup = { name ->
                val newRules = rules.map {
                    if (it.group == name) it.copy(group = BlockRuleGroupStore.DEFAULT_GROUP) else it
                }
                val newGroups = groups.filterNot { it == name }
                BlockRuleGroupStore.save(context, newGroups)
                saveRules(newRules)
                if (currentGroup == name) currentGroup = null
            },
            onDismiss = { showGroupManage = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pageCardContainerColor(),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                }
                Text(
                    text = stringResource(R.string.explore_block_rule_config),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    editingRule = BlockRule(
                        group = currentGroup ?: BlockRuleGroupStore.DEFAULT_GROUP,
                        enabled = true
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.explore_block_rule_add))
                }
                Box(modifier = Modifier.wrapContentSize()) {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.explore_block_rule_group_manage)) },
                            onClick = {
                                showMoreMenu = false
                                showGroupManage = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_str)) },
                            onClick = {
                                showMoreMenu = false
                                importFromClipboard(context) { refresh() }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_str)) },
                            onClick = {
                                showMoreMenu = false
                                exportToClipboard(context, filteredRules)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 分组筛选
            val allFilterGroups = remember(groups, rules) {
                val result = mutableListOf<String>()
                if (rules.any { BlockRuleGroupStore.isInBookSourceGroup(it) }) {
                    result.add(BlockRuleGroupStore.BOOK_SOURCE_GROUP)
                }
                if (rules.any { BlockRuleGroupStore.isInRssSourceGroup(it) }) {
                    result.add(BlockRuleGroupStore.RSS_SOURCE_GROUP)
                }
                result.addAll(groups)
                result
            }
            if (allFilterGroups.size > 1 || allFilterGroups.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactFilterChip(
                        selected = currentGroup == null,
                        onClick = { currentGroup = null },
                        label = stringResource(R.string.explore_block_rule_scope_all)
                    )
                    allFilterGroups.forEach { group ->
                        CompactFilterChip(
                            selected = currentGroup == group,
                            onClick = { currentGroup = group },
                            label = group
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 屏蔽规则总控开关
            CompactSwitchRow(
                text = stringResource(R.string.explore_block_rule_enable_master),
                checked = masterEnabled,
                onCheckedChange = { enabled ->
                    masterEnabled = enabled
                    context.putPrefBoolean(PreferKey.blockRuleEnabled, enabled)
                    BlockRuleStore.invalidateCache()
                    onRulesChanged()
                }
            )

            // 起效的屏蔽 - 默认同级色，有匹配时强调色
            val activeColor = if (currentMatchedRules.isNotEmpty()) MaterialTheme.colorScheme.primary else pageSecondaryTextColor()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showActiveRules = true }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.explore_block_rule_active_rules),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = activeColor
                )
                Text(
                    text = "${currentMatchedRules.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = activeColor
                )
            }

            // 显示屏蔽进度开关
            CompactSwitchRow(
                text = stringResource(R.string.explore_block_rule_show_progress),
                checked = showProgress,
                onCheckedChange = {
                    showProgress = it
                    context.putPrefBoolean(PreferKey.blockRuleShowProgress, it)
                    onShowProgressChanged(it)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 规则列表
            if (filteredRules.isEmpty()) {
                Text(
                    text = stringResource(R.string.explore_block_rule_empty),
                    modifier = Modifier.padding(12.dp),
                    color = pageSecondaryTextColor(),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val screenHeight = LocalDensity.current.run { context.resources.displayMetrics.heightPixels.toDp() }
                LazyColumn(
                    modifier = Modifier.heightIn(max = (screenHeight * 0.6f))
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        CompactBlockRuleItem(
                            rule = rule,
                            allSources = allSources,
                            allRssSources = allRssSources,
                            onToggleEnabled = {
                                val newRules = rules.map {
                                    if (it.id == rule.id) it.copy(enabled = !it.enabled) else it
                                }
                                saveRules(newRules)
                            },
                            onEdit = { editingRule = rule },
                            onDelete = { deletingRule = rule }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    // 起效规则弹窗
    if (showActiveRules) {
        AlertDialog(
            onDismissRequest = { showActiveRules = false },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = { Text(stringResource(R.string.explore_block_rule_active_rules), style = MaterialTheme.typography.titleMedium) },
            text = {
                if (currentMatchedRules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.explore_block_rule_active_rules_empty),
                        color = pageSecondaryTextColor(),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (allRssArticles.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(currentMatchedRules, key = { it.id }) { rule ->
                            val matchedArticles = allRssArticles.filter { article ->
                                rule.matchesRssArticle(article)
                            }
                            CompactActiveRssRuleItem(rule = rule, matchedArticles = matchedArticles)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(currentMatchedRules, key = { it.id }) { rule ->
                            val matchedBooks = allBooks.filter { book ->
                                rule.matches(book)
                            }
                            CompactActiveRuleItem(rule = rule, matchedBooks = matchedBooks)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActiveRules = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

// 紧凑的 FilterChip：选中时文字加粗+强调色，背景不变，保留边框
@Composable
private fun CompactFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val accent = MaterialTheme.colorScheme.primary
    val textColor = if (selected) accent else pageSecondaryTextColor()

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                fontSize = 12.sp
            ) 
        },
        modifier = Modifier.height(32.dp),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, accent)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, pageSecondaryTextColor().copy(alpha = 0.3f))
        }
    )
}

// 紧凑的 Switch 行
@Composable
private fun CompactSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// 紧凑的规则列表项
@Composable
private fun CompactBlockRuleItem(
    rule: BlockRule,
    allSources: List<BookSource>,
    allRssSources: List<RssSource>,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Text(
                text = rule.name.ifBlank { rule.pattern },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
            )
            Text(
                text = "${rule.modeLabel()} / ${rule.scopeSummary()} / ${rule.group}",
                style = MaterialTheme.typography.bodySmall,
                color = pageSecondaryTextColor(),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            if (rule.pattern.isNotBlank()) {
                Text(
                    text = rule.pattern,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// 起效规则项 - 紧凑版
@Composable
private fun CompactActiveRuleItem(rule: BlockRule, matchedBooks: List<SearchBook>) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { rule.pattern },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = "${rule.modeLabel()} / ${rule.scopeSummary()} / 匹配${matchedBooks.size}本",
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(18.dp),
                tint = pageSecondaryTextColor()
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                matchedBooks.forEach { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "《${book.name}》",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (book.author.isNotBlank()) {
                            Text(
                                text = " ${book.author}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = pageSecondaryTextColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActiveRssRuleItem(rule: BlockRule, matchedArticles: List<RssArticle>) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { rule.pattern },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = "${rule.modeLabel()} / ${rule.scopeSummary()} / 匹配${matchedArticles.size}条",
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(18.dp),
                tint = pageSecondaryTextColor()
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                matchedArticles.forEach { article ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = article.title.ifBlank { article.link },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (!article.pubDate.isNullOrBlank()) {
                            Text(
                                text = " ${article.pubDate}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = pageSecondaryTextColor(),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 规则编辑弹窗 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BlockRuleEditContent(
    sourceRule: BlockRule,
    groups: List<String>,
    onSave: (BlockRule) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(sourceRule.name) }
    var pattern by remember { mutableStateOf(sourceRule.pattern) }
    var isRegex by remember { mutableStateOf(sourceRule.isRegex) }
    var selectedGroup by remember { mutableStateOf(sourceRule.group.ifBlank { BlockRuleGroupStore.DEFAULT_GROUP }) }
    var targetScope by remember { mutableIntStateOf(sourceRule.targetScope) }
    var rssTargetScope by remember { mutableIntStateOf(sourceRule.rssTargetScope) }
    var scope by remember { mutableStateOf(sourceRule.scope.orEmpty()) }
    var rssScope by remember { mutableStateOf(sourceRule.rssScope.orEmpty()) }
    var patternError by remember { mutableStateOf<String?>(null) }
    var scopeError by remember { mutableStateOf<String?>(null) }
    var showScopeSelector by remember { mutableStateOf(false) }
    var showRssScopeSelector by remember { mutableStateOf(false) }
    var totalSourceCount by remember { mutableIntStateOf(0) }
    var totalRssSourceCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            totalSourceCount = appDb.bookSourceDao.getAllSources().size
            totalRssSourceCount = appDb.rssSourceDao.all.size
        }
    }

    val context = LocalContext.current
    val screenHeight = LocalDensity.current.run { context.resources.displayMetrics.heightPixels.toDp() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardContainerColor(),
        shape = RectangleShape,
        title = {
            Text(
                if (sourceRule.id.isBlank() || sourceRule.name.isBlank() && sourceRule.pattern.isBlank())
                    stringResource(R.string.explore_block_rule_add)
                else
                    stringResource(R.string.explore_block_rule_edit),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.75f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 规则名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.explore_block_rule_name), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.explore_block_rule_name_hint), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // 模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = {
                            pattern = it
                            patternError = if (it.isNotBlank() && isRegex) {
                                runCatching { Regex(it) }.exceptionOrNull()?.localizedMessage
                            } else null
                        },
                        label = { Text(stringResource(R.string.explore_block_rule_pattern), fontSize = 12.sp) },
                        placeholder = { Text(stringResource(R.string.explore_block_rule_pattern_hint), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = patternError != null,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = isRegex,
                            onCheckedChange = {
                                isRegex = it
                                patternError = if (pattern.isNotBlank() && it) {
                                    runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
                                } else null
                            }
                        )
                        Text(
                            text = stringResource(R.string.explore_block_rule_regex_mode),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
                patternError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                }

                // 分组选择
                var groupExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.explore_block_rule_group_default), fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false },
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group, fontSize = 13.sp) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                // 作用范围
                Text(
                    text = "作用范围",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                scopeError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 书源
                Text(
                    text = "书源",
                    style = MaterialTheme.typography.labelMedium,
                    color = pageSecondaryTextColor(),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val bookScopeOptions = listOf(
                        BlockRule.SCOPE_TITLE to stringResource(R.string.explore_block_rule_scope_title),
                        BlockRule.SCOPE_AUTHOR to stringResource(R.string.explore_block_rule_scope_author),
                        BlockRule.SCOPE_KIND to stringResource(R.string.explore_block_rule_scope_kind),
                        BlockRule.SCOPE_INTRO to stringResource(R.string.explore_block_rule_scope_intro),
                        BlockRule.SCOPE_WORD_COUNT to stringResource(R.string.explore_block_rule_scope_word_count),
                    )
                    bookScopeOptions.forEach { (flag, label) ->
                        CompactFilterChip(
                            selected = (targetScope and flag) != 0,
                            onClick = {
                                targetScope = targetScope xor flag
                                scopeError = null
                            },
                            label = label
                        )
                    }
                }

                // 作用的书源选择器 - 方案D
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showScopeSelector = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.explore_block_rule_source_scope),
                            fontSize = 11.sp,
                            color = pageSecondaryTextColor(),
                            lineHeight = 12.sp
                        )
                        Text(
                            text = if (scope.isBlank()) "全部书源" else {
                                val count = scope.split(";").map { it.trim() }.filter { it.isNotBlank() }.size
                                "已选 $count 个书源"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = "选择书源",
                        modifier = Modifier.size(18.dp),
                        tint = pageSecondaryTextColor()
                    )
                }

                // 订阅源
                Text(
                    text = "订阅源",
                    style = MaterialTheme.typography.labelMedium,
                    color = pageSecondaryTextColor(),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val rssScopeOptions = listOf(
                        BlockRule.SCOPE_RSS_TITLE to stringResource(R.string.explore_block_rule_scope_rss_title),
                        BlockRule.SCOPE_RSS_TIME to stringResource(R.string.explore_block_rule_scope_rss_time),
                    )
                    rssScopeOptions.forEach { (flag, label) ->
                        CompactFilterChip(
                            selected = (rssTargetScope and flag) != 0,
                            onClick = {
                                rssTargetScope = rssTargetScope xor flag
                                scopeError = null
                            },
                            label = label
                        )
                    }
                }

                // 作用的订阅源选择器 - 方案D
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRssScopeSelector = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.explore_block_rule_rss_source_scope),
                            fontSize = 11.sp,
                            color = pageSecondaryTextColor(),
                            lineHeight = 12.sp
                        )
                        Text(
                            text = if (rssScope.isBlank()) "全部订阅源" else {
                                val count = rssScope.split(";").map { it.trim() }.filter { it.isNotBlank() }.size
                                "已选 $count 个订阅源"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = "选择订阅源",
                        modifier = Modifier.size(18.dp),
                        tint = pageSecondaryTextColor()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && patternError == null,
                onClick = {
                    if (targetScope == 0 && rssTargetScope == 0) {
                        scopeError = "请至少选择一项作用范围"
                        return@TextButton
                    }
                    onSave(
                        sourceRule.copy(
                            id = sourceRule.id.ifBlank { System.currentTimeMillis().toString() },
                            name = name.ifBlank { pattern },
                            pattern = pattern,
                            isRegex = isRegex,
                            group = selectedGroup.ifBlank { BlockRuleGroupStore.DEFAULT_GROUP },
                            targetScope = targetScope,
                            rssTargetScope = rssTargetScope,
                            scope = scope.takeIf { it.isNotBlank() },
                            rssScope = rssScope.takeIf { it.isNotBlank() },
                        )
                    )
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )

    // 书源选择器
    if (showScopeSelector) {
        val currentScopeUrls = if (scope.isBlank()) emptySet()
        else scope.split(";").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        CompactSourceSelectorDialog(
            title = stringResource(R.string.explore_block_rule_source_scope),
            initialSelectedUrls = currentScopeUrls,
            defaultSelectAll = false,
            isRss = false,
            onConfirm = { selectedUrls ->
                scope = if (selectedUrls.isEmpty() || selectedUrls.size == totalSourceCount) {
                    ""
                } else {
                    selectedUrls.joinToString(";")
                }
                showScopeSelector = false
            },
            onDismiss = { showScopeSelector = false }
        )
    }

    // 订阅源选择器
    if (showRssScopeSelector) {
        val currentRssScopeUrls = if (rssScope.isBlank()) emptySet()
        else rssScope.split(";").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        CompactSourceSelectorDialog(
            title = stringResource(R.string.explore_block_rule_rss_source_scope),
            initialSelectedUrls = currentRssScopeUrls,
            defaultSelectAll = false,
            isRss = true,
            onConfirm = { selectedUrls ->
                rssScope = if (selectedUrls.isEmpty() || selectedUrls.size == totalRssSourceCount) {
                    ""
                } else {
                    selectedUrls.joinToString(";")
                }
                showRssScopeSelector = false
            },
            onDismiss = { showRssScopeSelector = false }
        )
    }
}

/** 分组管理弹窗 */
@Composable
private fun BlockRuleGroupManageContent(
    groups: List<String>,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (oldName: String, newName: String) -> Unit,
    onDeleteGroup: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var localGroups by remember { mutableStateOf(groups) }
    var inputDialog by remember { mutableStateOf<GroupInput?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }

    inputDialog?.let { dialog ->
        var inputName by remember { mutableStateOf(dialog.initialName) }
        AlertDialog(
            onDismissRequest = { inputDialog = null },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = { Text(dialog.title, style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = inputName.isNotBlank(),
                    onClick = {
                        dialog.onConfirm(inputName.trim())
                        inputDialog = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { inputDialog = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    deleteDialog?.let { groupName ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = null,
            text = {
                Text(
                    "确定删除分组「$groupName」？规则将移至默认分组。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGroup(groupName)
                        localGroups = localGroups.filterNot { it == groupName }
                        deleteDialog = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardContainerColor(),
        shape = RectangleShape,
        title = { Text(stringResource(R.string.explore_block_rule_group_manage), style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(localGroups) { group ->
                    val isReserved = BlockRuleGroupStore.isReservedGroup(group)
                    ListItem(
                        headlineContent = {
                            Text(
                                group,
                                color = if (isReserved) pageSecondaryTextColor() else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        },
                        trailingContent = {
                            if (!isReserved) {
                                Row {
                                    IconButton(onClick = {
                                        inputDialog = GroupInput("重命名分组", group) { newName ->
                                            onRenameGroup(group, newName)
                                            localGroups = localGroups.map { if (it == group) newName else it }
                                        }
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { deleteDialog = group }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                inputDialog = GroupInput("添加分组") { name ->
                    onAddGroup(name)
                    localGroups = (localGroups + name).distinct()
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加", fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** 统一的书源/订阅源选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSourceSelectorDialog(
    title: String,
    initialSelectedUrls: Set<String>,
    defaultSelectAll: Boolean = false,
    isRss: Boolean,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var allSources by remember { mutableStateOf<List<BookSource>>(emptyList()) }
    var allRssSources by remember { mutableStateOf<List<RssSource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUrls by remember { mutableStateOf(initialSelectedUrls) }
    var defaultSelectAllPending by remember { mutableStateOf(defaultSelectAll && initialSelectedUrls.isEmpty()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (isRss) {
                val sources = appDb.rssSourceDao.all
                withContext(Dispatchers.Main) {
                    allRssSources = sources
                    if (defaultSelectAllPending) {
                        selectedUrls = sources.map { it.sourceUrl }.toSet()
                        defaultSelectAllPending = false
                    }
                    isLoading = false
                }
            } else {
                val sources = appDb.bookSourceDao.getAllSources()
                withContext(Dispatchers.Main) {
                    allSources = sources
                    if (defaultSelectAllPending) {
                        selectedUrls = sources.map { it.bookSourceUrl }.toSet()
                        defaultSelectAllPending = false
                    }
                    isLoading = false
                }
            }
        }
    }

    val filteredSources = remember(allSources, allRssSources, searchQuery) {
        if (isRss) {
            if (searchQuery.isBlank()) allRssSources
            else allRssSources.filter {
                it.sourceName.contains(searchQuery, ignoreCase = true) ||
                        it.sourceUrl.contains(searchQuery, ignoreCase = true)
            }
        } else {
            if (searchQuery.isBlank()) allSources
            else allSources.filter {
                it.bookSourceName.contains(searchQuery, ignoreCase = true) ||
                        it.bookSourceUrl.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalCount = if (isRss) allRssSources.size else allSources.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardContainerColor(),
        shape = RectangleShape,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (isLoading) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (totalCount == 0) {
                Text(
                    if (isRss) "暂无订阅源" else "暂无书源",
                    color = pageSecondaryTextColor(),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索名称或URL", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            selectedUrls = if (isRss) {
                                filteredSources.map { (it as RssSource).sourceUrl }.toSet()
                            } else {
                                filteredSources.map { (it as BookSource).bookSourceUrl }.toSet()
                            }
                        }) {
                            Text("全选", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            val filteredUrls = if (isRss) {
                                filteredSources.map { (it as RssSource).sourceUrl }.toSet()
                            } else {
                                filteredSources.map { (it as BookSource).bookSourceUrl }.toSet()
                            }
                            val newSelected = selectedUrls.toMutableSet()
                            filteredUrls.forEach { url ->
                                if (url in newSelected) newSelected.remove(url) else newSelected.add(url)
                            }
                            selectedUrls = newSelected
                        }) {
                            Text("反选", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${selectedUrls.size}/$totalCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = pageSecondaryTextColor(),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val listState = rememberLazyListState()
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart)
                        ) {
                            items(filteredSources, key = { 
                                if (isRss) (it as RssSource).sourceUrl else (it as BookSource).bookSourceUrl 
                            }) { source ->
                                val url = if (isRss) (source as RssSource).sourceUrl else (source as BookSource).bookSourceUrl
                                val name = if (isRss) (source as RssSource).sourceName.ifBlank { url } else (source as BookSource).bookSourceName.ifBlank { url }
                                val displayUrl = if (isRss) (source as RssSource).sourceUrl else (source as BookSource).bookSourceUrl
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedUrls = if (url in selectedUrls) {
                                                selectedUrls - url
                                            } else {
                                                selectedUrls + url
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = url in selectedUrls,
                                        onCheckedChange = { checked ->
                                            selectedUrls = if (checked) {
                                                selectedUrls + url
                                            } else {
                                                selectedUrls - url
                                            }
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 12.sp
                                        )
                                        if (name != displayUrl) {
                                            Text(
                                                text = displayUrl,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = pageSecondaryTextColor(),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(state = listState, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedUrls) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private data class GroupInput(
    val title: String,
    val initialName: String = "",
    val onConfirm: (String) -> Unit
)

private fun importFromClipboard(context: android.content.Context, onRefresh: () -> Unit) {
    val clip = context.getClipText()
    if (clip.isNullOrBlank()) {
        context.toastOnUi(R.string.explore_block_rule_clipboard_empty)
        return
    }
    val imported = GSON.fromJsonArray<BlockRule>(clip).getOrNull()
    if (imported.isNullOrEmpty()) {
        context.toastOnUi(R.string.explore_block_rule_import_invalid)
        return
    }
    val existing = BlockRuleStore.load(context)
    val existingIds = existing.map { it.id }.toSet()

    val newRules = imported
        .map { BlockRuleStore.sanitizeRule(it) }
        .filter { it.id !in existingIds }

    if (newRules.isEmpty()) {
        context.toastOnUi("导入的规则已存在，无新规则")
        return
    }

    BlockRuleStore.save(context, existing + newRules)
    context.toastOnUi("成功导入 ${newRules.size} 条规则")
    onRefresh()
}

private fun exportToClipboard(context: android.content.Context, rules: List<BlockRule>) {
    if (rules.isEmpty()) {
        context.toastOnUi(R.string.explore_block_rule_empty)
        return
    }
    context.sendToClip(GSON.toJson(rules))
    context.toastOnUi(R.string.explore_block_rule_export_success)
}