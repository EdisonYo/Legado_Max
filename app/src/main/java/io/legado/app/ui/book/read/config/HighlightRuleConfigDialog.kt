package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 高亮规则配置弹窗（Compose 重构）
 * 合并原 HighlightRuleConfigDialog + EditDialog + GroupManageDialog + PresetRuleDialog
 */
class HighlightRuleConfigDialog : DialogFragment(), ColorPickerDialogListener {

    private var pendingColorCallback: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    HighlightRuleConfigContent(
                        onDismiss = { dismissAllowingStateLoss() },
                        showColorPicker = { currentColor, onSelected ->
                            showColorPicker(currentColor, onSelected)
                        }
                    )
                }
            }
        }
    }

    fun showColorPicker(currentColor: Int, onSelected: (Int) -> Unit) {
        pendingColorCallback = onSelected
        ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setColor(currentColor)
            .setShowAlphaSlider(false)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setDialogId(1)
            .create()
            .apply { setColorPickerDialogListener(this@HighlightRuleConfigDialog) }
            .show(parentFragmentManager, "color_picker")
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        pendingColorCallback?.invoke(color)
        pendingColorCallback = null
    }

    override fun onDialogDismissed(dialogId: Int) {
        pendingColorCallback = null
    }
}

// ==================== 页面路由 ====================

private sealed class SheetPage {
    data object List : SheetPage()
    data class Edit(val rule: HighlightRule?) : SheetPage()
    data object Groups : SheetPage()
    data object Presets : SheetPage()
}

// ==================== 主内容 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HighlightRuleConfigContent(
    onDismiss: () -> Unit,
    showColorPicker: (Int, (Int) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf<List<HighlightRule>>(HighlightRuleRepository.load(context)) }
    var currentGroup by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf<SheetPage>(SheetPage.List) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var deletingRule by remember { mutableStateOf<HighlightRule?>(null) }
    var showShareSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = context.getPrefString(PreferKey.highlightRuleCurrentGroup)
        if (!saved.isNullOrBlank()) {
            val groups = HighlightRuleRepository.loadGroups(context)
            if (groups.contains(saved)) {
                currentGroup = saved
            }
        }
    }

    fun refreshRules() {
        HighlightRuleRepository.load(context).let { loaded ->
            rules = loaded
        }
    }

    fun saveRules(newRules: List<HighlightRule>) {
        HighlightRuleRepository.save(context, newRules)
        rules = newRules
    }

    fun saveCurrentGroup() {
        context.putPrefString(PreferKey.highlightRuleCurrentGroup, currentGroup.orEmpty())
    }

    val filteredRules = when (currentGroup) {
        null -> rules
        else -> rules.filter { it.group == currentGroup }
    }
    val groups = remember(rules) { HighlightRuleRepository.loadGroups(context) }

    // 删除确认
    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = null,
            text = {
                Text(
                    text = "确定删除「${rule.name.ifBlank { rule.pattern }}」吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HighlightRuleRepository.recycleRules(context, listOf(rule))
                        saveRules(rules.filterNot { it.id == rule.id })
                        deletingRule = null
                    }
                ) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // 分享选择器
    if (showShareSelector) {
        val selected = BooleanArray(rules.size)
        val names = rules.map {
            "${it.name.ifBlank { "未命名规则" }} / ${it.group}"
        }.toTypedArray()
        AlertDialog(
            onDismissRequest = { showShareSelector = false },
            containerColor = pageCardContainerColor(),
            shape = RectangleShape,
            title = { Text("选择规则", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    names.forEachIndexed { index, name ->
                        var checked by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = !checked
                                    selected[index] = checked
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    selected[index] = it
                                },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = rules.filterIndexed { index, _ -> selected[index] }
                    if (picked.isEmpty()) {
                        context.toastOnUi("请先选择规则")
                    } else {
                        shareRules(context, picked)
                    }
                    showShareSelector = false
                }) {
                    Text("分享选中")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareSelector = false }) {
                    Text("删除选中")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pageCardContainerColor(),
        shape = RectangleShape
    ) {
        when (page) {
            is SheetPage.List -> {
                RuleListPage(
                    rules = rules,
                    filteredRules = filteredRules,
                    groups = groups,
                    currentGroup = currentGroup,
                    onGroupChange = {
                        currentGroup = it
                        saveCurrentGroup()
                    },
                    onAddRule = {
                        page = SheetPage.Edit(null)
                    },
                    onEditRule = { rule ->
                        page = SheetPage.Edit(rule)
                    },
                    onDeleteRule = { rule ->
                        deletingRule = rule
                    },
                    onToggleEnabled = { rule ->
                        val newRules = rules.map {
                            if (it.id == rule.id) it.copy(enabled = !it.enabled) else it
                        }
                        saveRules(newRules)
                    },
                    onShowMoreMenu = { showMoreMenu = true },
                    onDismiss = onDismiss,
                    onShowPresets = { page = SheetPage.Presets },
                    onShowGroupManage = { page = SheetPage.Groups },
                    onImport = { importFromClipboard(context) { refreshRules() } },
                    onExport = { exportToClipboard(context, filteredRules) },
                    onShare = { showShareSelector = true },
                    onReset = {
                        saveRules(HighlightRuleRepository.reset(context))
                    }
                )
            }
            is SheetPage.Edit -> {
                val editRule = (page as SheetPage.Edit).rule
                RuleEditPage(
                    sourceRule = editRule,
                    groups = groups,
                    onSave = { newRule ->
                        val index = rules.indexOfFirst { it.id == newRule.id }
                        val newRules = if (index >= 0) {
                            rules.toMutableList().also { it[index] = newRule }
                        } else {
                            rules + newRule
                        }
                        saveRules(newRules)
                        page = SheetPage.List
                    },
                    onDismiss = { page = SheetPage.List },
                    showColorPicker = showColorPicker
                )
            }
            SheetPage.Groups -> {
                GroupManagePage(
                    groups = groups,
                    rules = rules,
                    onChanged = { oldGroup, newGroup ->
                        if (oldGroup != null && currentGroup == oldGroup) {
                            currentGroup = newGroup
                        }
                        refreshRules()
                    },
                    onDismiss = { page = SheetPage.List }
                )
            }
            SheetPage.Presets -> {
                PresetRulePage(
                    defaultGroup = currentGroup,
                    onAdd = { rule ->
                        saveRules(rules + rule)
                        page = SheetPage.List
                    },
                    onDismiss = { page = SheetPage.List }
                )
            }
        }
    }
}

// ==================== 规则列表页 ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleListPage(
    rules: List<HighlightRule>,
    filteredRules: List<HighlightRule>,
    groups: List<String>,
    currentGroup: String?,
    onGroupChange: (String?) -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (HighlightRule) -> Unit,
    onDeleteRule: (HighlightRule) -> Unit,
    onToggleEnabled: (HighlightRule) -> Unit,
    onShowMoreMenu: () -> Unit,
    onDismiss: () -> Unit,
    onShowPresets: () -> Unit,
    onShowGroupManage: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit
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
                text = stringResource(R.string.highlight_rule_config),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddRule) {
                Icon(Icons.Filled.Add, contentDescription = "添加规则")
            }
            Box(modifier = Modifier.wrapContentSize()) {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("预设规则", fontSize = 13.sp) },
                        onClick = { showMenu = false; onShowPresets() }
                    )
                    DropdownMenuItem(
                        text = { Text("分组管理", fontSize = 13.sp) },
                        onClick = { showMenu = false; onShowGroupManage() }
                    )
                    DropdownMenuItem(
                        text = { Text("导入", fontSize = 13.sp) },
                        onClick = { showMenu = false; onImport() }
                    )
                    DropdownMenuItem(
                        text = { Text("导出", fontSize = 13.sp) },
                        onClick = { showMenu = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("分享", fontSize = 13.sp) },
                        onClick = { showMenu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("恢复默认", fontSize = 13.sp) },
                        onClick = { showMenu = false; onReset() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 分组筛选
        if (groups.size > 1 || groups.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompactFilterChip(
                    selected = currentGroup == null,
                    onClick = { onGroupChange(null) },
                    label = "全部分组"
                )
                groups.forEach { group ->
                    CompactFilterChip(
                        selected = currentGroup == group,
                        onClick = { onGroupChange(group) },
                        label = group
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 副标题
        val groupText = currentGroup ?: "全部分组"
        Text(
            text = "$groupText · ${filteredRules.size} 条规则",
            style = MaterialTheme.typography.bodySmall,
            color = pageSecondaryTextColor(),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp)

        // 规则列表
        if (filteredRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无规则，点击右上角添加",
                    color = pageSecondaryTextColor(),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp
                )
            }
        } else {
            val screenHeight = LocalDensity.current.run {
                context.resources.displayMetrics.heightPixels.toDp()
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = screenHeight * 0.55f)
            ) {
                items(filteredRules, key = { it.id }) { rule ->
                    CompactRuleItem(
                        rule = rule,
                        onToggleEnabled = { onToggleEnabled(rule) },
                        onEdit = { onEditRule(rule) },
                        onDelete = { onDeleteRule(rule) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

// ==================== 规则编辑页 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditPage(
    sourceRule: HighlightRule?,
    groups: List<String>,
    onSave: (HighlightRule) -> Unit,
    onDismiss: () -> Unit,
    showColorPicker: (Int, (Int) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var editingRule by remember {
        mutableStateOf(
            sourceRule?.copy() ?: HighlightRule(
                group = groups.firstOrNull() ?: HighlightRuleRepository.DEFAULT_GROUP
            )
        )
    }
    var name by remember { mutableStateOf(editingRule.name) }
    var pattern by remember { mutableStateOf(editingRule.pattern) }
    var sampleText by remember { mutableStateOf(editingRule.sampleText) }
    var selectedGroup by remember { mutableStateOf(editingRule.group) }
    var targetScope by remember { mutableIntStateOf(editingRule.targetScope) }
    var enabled by remember { mutableStateOf(editingRule.enabled) }
    var textColor by remember { mutableStateOf(editingRule.textColor) }
    var underlineMode by remember { mutableIntStateOf(editingRule.underlineMode) }
    var underlineColor by remember { mutableStateOf(editingRule.underlineColor) }
    var underlineWidth by remember { mutableFloatStateOf(editingRule.underlineWidth) }
    var underlineOffset by remember { mutableFloatStateOf(editingRule.underlineOffset) }
    var underlineSvgPath by remember { mutableStateOf(editingRule.underlineSvgPath.orEmpty()) }
    var bgImage by remember { mutableStateOf(editingRule.bgImage.orEmpty()) }
    var bgImageFit by remember { mutableIntStateOf(editingRule.bgImageFit) }
    var bgImageScale by remember { mutableFloatStateOf(editingRule.bgImageScale) }
    var patternError by remember { mutableStateOf<String?>(null) }
    var groupExpanded by remember { mutableStateOf(false) }

    val selectImageLauncher = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            // 需要 RealPathUtil 和 TextLine.copyBgImageToInternal
            // 这里简化处理，实际需要 import
            bgImage = uri.toString()
        }
    }

    fun validatePattern(): String? {
        return if (pattern.isBlank()) "正则表达式不能为空"
        else kotlin.runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
    }

    fun buildPreviewRule(): HighlightRule {
        return editingRule.copy(
            name = name,
            pattern = pattern,
            sampleText = sampleText,
            group = selectedGroup,
            targetScope = targetScope,
            enabled = enabled,
            textColor = textColor,
            underlineMode = underlineMode,
            underlineColor = underlineColor,
            underlineWidth = underlineWidth,
            underlineOffset = underlineOffset,
            underlineSvgPath = underlineSvgPath.takeIf { it.isNotBlank() },
            bgImage = bgImage.takeIf { it.isNotBlank() },
            bgImageFit = bgImageFit,
            bgImageScale = bgImageScale
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardContainerColor(),
        shape = RectangleShape,
        title = {
            Text(
                if (sourceRule == null) "添加规则" else "编辑规则",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 启用开关
                CompactSwitchRow(
                    text = "启用规则",
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )

                // 规则名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称", fontSize = 12.sp) },
                    placeholder = { Text("如：对话高亮", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // 正则表达式
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        patternError = validatePattern()
                    },
                    label = { Text("正则表达式", fontSize = 12.sp) },
                    placeholder = { Text("输入匹配模式", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = patternError != null && pattern.isNotBlank(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                patternError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }

                // 分组选择
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分组", fontSize = 12.sp) },
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
                Text("作用范围", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to "全部",
                        1 to "仅标题",
                        2 to "仅正文"
                    ).forEach { (value, label) ->
                        CompactFilterChip(
                            selected = targetScope == value,
                            onClick = { targetScope = value },
                            label = label
                        )
                    }
                }

                // 文字颜色
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("文字颜色", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = androidx.compose.ui.graphics.Color(textColor ?: Color.GRAY),
                                shape = RectangleShape
                            )
                            .clickable {
                                showColorPicker(textColor ?: Color.BLACK) { textColor = it }
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { textColor = null }
                    ) {
                        Text("清除", fontSize = 12.sp)
                    }
                }

                // 下划线模式
                Text("下划线样式", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to "无",
                        1 to "实线",
                        2 to "虚线",
                        3 to "波浪",
                        4 to "双线",
                        5 to "SVG"
                    ).forEach { (value, label) ->
                        CompactFilterChip(
                            selected = underlineMode == value,
                            onClick = { underlineMode = value },
                            label = label
                        )
                    }
                }

                // 下划线颜色
                if (underlineMode != 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("下划线颜色", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color(underlineColor ?: Color.GRAY),
                                    shape = RectangleShape
                                )
                                .clickable {
                                    showColorPicker(underlineColor ?: Color.BLACK) { underlineColor = it }
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { underlineColor = null }
                        ) {
                            Text("清除", fontSize = 12.sp)
                        }
                    }
                }

                // SVG 路径
                if (underlineMode == 5) {
                    OutlinedTextField(
                        value = underlineSvgPath,
                        onValueChange = { underlineSvgPath = it },
                        label = { Text("SVG 路径", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }

                // 背景图
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = bgImage,
                        onValueChange = { bgImage = it },
                        label = { Text("背景图片", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            selectImageLauncher.launch {
                                mode = HandleFileContract.IMAGE
                                title = "选择背景图片"
                            }
                        }
                    ) {
                        Text("选择", fontSize = 12.sp)
                    }
                }

                // 背景图适配
                if (bgImage.isNotBlank()) {
                    Text("背景图适配", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to "平铺",
                            1 to "拉伸",
                            2 to "裁剪"
                        ).forEach { (value, label) ->
                            CompactFilterChip(
                                selected = bgImageFit == value,
                                onClick = { bgImageFit = value },
                                label = label
                            )
                        }
                    }
                    // 缩放
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("缩放", modifier = Modifier.width(40.dp), fontSize = 12.sp)
                        Slider(
                            value = bgImageScale,
                            onValueChange = { bgImageScale = it },
                            valueRange = 0.1f..5f,
                            steps = 48,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${String.format("%.1f", bgImageScale)}x", fontSize = 11.sp, modifier = Modifier.width(36.dp))
                    }
                }

                // 示例文本
                OutlinedTextField(
                    value = sampleText,
                    onValueChange = { sampleText = it },
                    label = { Text("示例文本", fontSize = 12.sp) },
                    placeholder = { Text("用于预览效果", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    maxLines = 3
                )

                // 预览
                Text("预览", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val previewRule = buildPreviewRule()
                val previewSpannable = remember(previewRule) {
                    HighlightRulePreview.build(previewRule)
                }
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            textSize = 14f
                            setTextColor(Color.BLACK)
                        }
                    },
                    update = { textView ->
                        textView.text = previewSpannable
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && patternError == null,
                onClick = {
                    onSave(
                        editingRule.copy(
                            id = editingRule.id.ifBlank { System.currentTimeMillis().toString() },
                            name = name.ifBlank { pattern },
                            pattern = pattern,
                            sampleText = sampleText,
                            group = selectedGroup,
                            targetScope = targetScope,
                            enabled = enabled,
                            textColor = textColor,
                            underlineMode = underlineMode,
                            underlineColor = underlineColor,
                            underlineWidth = underlineWidth,
                            underlineOffset = underlineOffset,
                            underlineSvgPath = underlineSvgPath.takeIf { underlineMode == 5 && it.isNotBlank() },
                            bgImage = bgImage.takeIf { it.isNotBlank() },
                            bgImageFit = bgImageFit,
                            bgImageScale = bgImageScale
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
}

// ==================== 分组管理页 ====================

@Composable
private fun GroupManagePage(
    groups: List<String>,
    rules: List<HighlightRule>,
    onChanged: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                TextButton(onClick = { inputDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
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
                    "删除后，该分组下的规则会移动到默认分组。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newGroups = localGroups.filterNot { it == groupName }
                        val newRules = rules.map {
                            if (it.group == groupName) it.copy(group = HighlightRuleRepository.DEFAULT_GROUP) else it
                        }
                        HighlightRuleRepository.saveGroups(context, newGroups)
                        HighlightRuleRepository.save(context, newRules)
                        localGroups = newGroups
                        onChanged(groupName, null)
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
        title = { Text("分组管理", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                localGroups.forEach { group ->
                    val isDefault = group == HighlightRuleRepository.DEFAULT_GROUP
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = if (isDefault) pageSecondaryTextColor() else MaterialTheme.colorScheme.onSurface
                        )
                        if (!isDefault) {
                            IconButton(
                                onClick = {
                                    inputDialog = GroupInput("重命名分组", group) { newName ->
                                        val newRules = rules.map {
                                            if (it.group == group) it.copy(group = newName) else it
                                        }
                                        val newGroups = localGroups.map { if (it == group) newName else it }
                                        HighlightRuleRepository.saveGroups(context, newGroups)
                                        HighlightRuleRepository.save(context, newRules)
                                        localGroups = newGroups
                                        onChanged(group, newName)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { deleteDialog = group },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    inputDialog = GroupInput("添加分组") { name ->
                        val newGroups = (localGroups + name).distinct()
                        HighlightRuleRepository.saveGroups(context, newGroups)
                        localGroups = newGroups
                        onChanged(null, null)
                    }
                }
            ) {
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

// ==================== 预设规则页 ====================

@Composable
private fun PresetRulePage(
    defaultGroup: String?,
    onAdd: (HighlightRule) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val presetRules = remember { HighlightRuleRepository.defaultPresetRules(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pageCardContainerColor(),
        shape = RectangleShape,
        title = { Text("预设规则", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                presetRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                rule.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                rule.pattern,
                                fontSize = 11.sp,
                                color = pageSecondaryTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                onAdd(
                                    rule.copy(
                                        id = System.currentTimeMillis().toString(),
                                        group = defaultGroup ?: HighlightRuleRepository.DEFAULT_GROUP
                                    )
                                )
                            }
                        ) {
                            Text("添加", fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

// ==================== 通用组件 ====================

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
            BorderStroke(1.dp, accent)
        } else {
            BorderStroke(1.dp, pageSecondaryTextColor().copy(alpha = 0.3f))
        }
    )
}

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
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun CompactRuleItem(
    rule: HighlightRule,
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
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = rule.name.ifBlank { rule.pattern },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
            )
            Text(
                text = rule.styleSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = pageSecondaryTextColor(),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Text(
                text = "${rule.group} / ${rule.targetScopeLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = pageSecondaryTextColor(),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
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

// ==================== 数据类 & 工具函数 ====================

private data class GroupInput(
    val title: String,
    val initialName: String = "",
    val onConfirm: (String) -> Unit
)

private fun importFromClipboard(context: android.content.Context, onRefresh: () -> Unit) {
    val clip = context.getClipText()
    if (clip.isNullOrBlank()) {
        context.toastOnUi("剪贴板为空")
        return
    }
    val imported = GSON.fromJsonArray<HighlightRule>(clip).getOrNull()
    if (imported.isNullOrEmpty()) {
        context.toastOnUi("导入内容无效")
        return
    }
    val existing = HighlightRuleRepository.load(context)
    val newRules = imported.map { rule ->
        var normalized = HighlightRuleRepository.sanitizeRule(rule)
        if (existing.any { it.id == normalized.id }) {
            normalized = normalized.copyWithNewId()
        }
        normalized
    }
    HighlightRuleRepository.save(context, existing + newRules)
    context.toastOnUi("成功导入 ${newRules.size} 条规则")
    onRefresh()
}

private fun exportToClipboard(context: android.content.Context, rules: List<HighlightRule>) {
    if (rules.isEmpty()) {
        context.toastOnUi("暂无规则可导出")
        return
    }
    context.sendToClip(GSON.toJson(rules))
    context.toastOnUi("已复制 ${rules.size} 条规则")
}

private fun shareRules(context: android.content.Context, rules: List<HighlightRule>) {
    if (rules.isEmpty()) {
        context.toastOnUi("没有可分享的规则")
        return
    }
    val json = GSON.toJson(rules)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, json)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享规则"))
}
