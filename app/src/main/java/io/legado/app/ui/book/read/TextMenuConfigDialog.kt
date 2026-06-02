package io.legado.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本菜单项配置对话框 - Compose实现
 *
 * 功能：
 * 1. Tab 切换「内置菜单」和「其它应用菜单」（Android 6.0+）
 * 2. 支持调整可见菜单项数量（3~10）
 * 3. 支持自定义菜单项标题
 * 4. 所有更改在点击「确定」后统一生效
 * 5. 点击外部/返回键直接关闭，不保存任何更改
 */
class TextMenuConfigDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    TextMenuConfigDialogContent(
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMenuConfigDialogContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val menuItems = remember { TextMenuConfig.getAllMenuItems() }

    // 内存状态（确定时才持久化）
    var hiddenIds by remember { mutableStateOf(TextMenuConfig.getHiddenMenuItemIds(context)) }
    var hiddenProcessItems by remember { mutableStateOf(TextMenuConfig.getHiddenProcessTextItems(context)) }
    var customTitles by remember { mutableStateOf(TextMenuConfig.getCustomMenuTitles(context)) }
    var processCustomTitles by remember { mutableStateOf(TextMenuConfig.getCustomProcessTextTitles(context)) }
    var visibleCount by remember { mutableIntStateOf(TextMenuConfig.getTextMenuVisibleCount(context)) }
    var selectedTab by remember { mutableStateOf(0) }

    // 编辑对话框状态
    var editingMenuItem by remember { mutableStateOf<TextMenuConfig.MenuItemInfo?>(null) }
    var editingProcessApp by remember { mutableStateOf<<ProcessTextAppInfo?>(null) }

    // 异步加载其它应用列表
    var processTextApps by remember { mutableStateOf<List<<ProcessTextAppInfo>>(emptyList()) }
    var isLoadingProcessApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isLoadingProcessApps = true
            processTextApps = withContext(Dispatchers.IO) {
                getProcessTextApps(context)
            }
            isLoadingProcessApps = false
        }
    }

    val showTabs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    // 编辑内置菜单标题对话框
    editingMenuItem?.let { item ->
        EditMenuTitleDialog(
            title = customTitles[item.id] ?: context.getString(item.nameResId),
            defaultTitle = context.getString(item.nameResId),
            onDismiss = { editingMenuItem = null },
            onConfirm = { newTitle ->
                customTitles = customTitles.toMutableMap().apply {
                    val trimmed = newTitle.trim()
                    if (trimmed.isEmpty() || trimmed == context.getString(item.nameResId)) {
                        remove(item.id)
                    } else {
                        put(item.id, trimmed)
                    }
                }
                editingMenuItem = null
            }
        )
    }

    // 编辑其它应用标题对话框
    editingProcessApp?.let { app ->
        EditMenuTitleDialog(
            title = processCustomTitles[app.key] ?: app.label,
            defaultTitle = app.label,
            onDismiss = { editingProcessApp = null },
            onConfirm = { newTitle ->
                processCustomTitles = processCustomTitles.toMutableMap().apply {
                    val trimmed = newTitle.trim()
                    if (trimmed.isEmpty() || trimmed == app.label) {
                        remove(app.key)
                    } else {
                        put(app.key, trimmed)
                    }
                }
                editingProcessApp = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 工具栏：无返回按钮，不加粗，颜色走主题 secondary
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.text_menu_config),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                )

                // Tab 切换（Android 6.0+ 始终显示，避免加载后突然冒出）
                if (showTabs) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.text_menu_config)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.process_text_menu_config)) }
                        )
                    }
                }

                Text(
                    text = if (selectedTab == 0) {
                        stringResource(R.string.text_menu_config_desc)
                    } else {
                        stringResource(R.string.process_text_menu_config_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 可见数量调整（仅内置菜单 Tab）
                if (selectedTab == 0) {
                    TextMenuVisibleCountRow(
                        count = visibleCount,
                        onCountChange = {
                            visibleCount = it.coerceIn(
                                TextMenuConfig.MIN_VISIBLE_COUNT,
                                TextMenuConfig.MAX_VISIBLE_COUNT
                            )
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (selectedTab == 0) {
                        items(menuItems) { item ->
                            val title = customTitles[item.id] ?: context.getString(item.nameResId)
                            ConfigItemRow(
                                title = title,
                                subtitle = "ID: ${item.id}",
                                isChecked = item.id !in hiddenIds,
                                onEditClick = { editingMenuItem = item },
                                onCheckedChange = { checked ->
                                    hiddenIds = hiddenIds.toMutableSet().apply {
                                        if (checked) remove(item.id) else add(item.id)
                                    }
                                }
                            )
                        }
                    } else {
                        when {
                            isLoadingProcessApps -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                            processTextApps.isEmpty() -> {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_process_text_apps),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            else -> {
                                items(processTextApps) { app ->
                                    val title = processCustomTitles[app.key] ?: app.label
                                    ConfigItemRow(
                                        title = title,
                                        subtitle = app.packageName,
                                        isChecked = app.key !in hiddenProcessItems,
                                        onEditClick = { editingProcessApp = app },
                                        onCheckedChange = { checked ->
                                            hiddenProcessItems = hiddenProcessItems.toMutableSet().apply {
                                                if (checked) remove(app.key) else add(app.key)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            hiddenIds = emptySet()
                            hiddenProcessItems = emptySet()
                            customTitles = emptyMap()
                            processCustomTitles = emptyMap()
                            visibleCount = TextMenuConfig.DEFAULT_VISIBLE_COUNT
                        }
                    ) {
                        Text(text = stringResource(R.string.reset_to_default))
                    }

                    TextButton(
                        onClick = {
                            TextMenuConfig.setHiddenMenuItemIds(context, hiddenIds)
                            TextMenuConfig.setHiddenProcessTextItems(context, hiddenProcessItems)
                            TextMenuConfig.setTextMenuVisibleCount(context, visibleCount)
                            context.putPrefString(
                                PreferKey.textMenuCustomTitles,
                                GSON.toJson(customTitles)
                            )
                            context.putPrefString(
                                PreferKey.processTextCustomTitles,
                                GSON.toJson(processCustomTitles)
                            )
                            context.toastOnUi("已保存")
                            onDismiss()
                        }
                    ) {
                        Text(text = stringResource(R.string.dialog_confirm))
                    }
                }
            }
        }
    }
}

/**
 * 可见数量调整行
 */
@Composable
fun TextMenuVisibleCountRow(
    count: Int,
    onCountChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.text_menu_visible_count),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.text_menu_visible_count_desc, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > TextMenuConfig.MIN_VISIBLE_COUNT
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.reduce)
                )
            }

            Box(
                modifier = Modifier.widthIn(min = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { onCountChange(count + 1) },
                enabled = count < TextMenuConfig.MAX_VISIBLE_COUNT
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plus)
                )
            }
        }
    }
}

/**
 * 编辑标题对话框
 */
@Composable
fun EditMenuTitleDialog(
    title: String,
    defaultTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(title) { mutableStateOf(title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        title = {
            Text(text = stringResource(R.string.text_menu_edit_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = {
                        Text(text = stringResource(R.string.text_menu_edit_name))
                    },
                    placeholder = {
                        Text(text = defaultTitle)
                    }
                )
                Text(
                    text = stringResource(R.string.text_menu_edit_name_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * 其它应用信息
 */
data class ProcessTextAppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val className: String
)

/**
 * 获取能处理 ACTION_PROCESS_TEXT 的应用列表
 */
@Suppress("DEPRECATION")
private fun getProcessTextApps(context: Context): List<<ProcessTextAppInfo> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return emptyList()
    }

    val intent = Intent()
        .setAction(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")

    return try {
        val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfoList.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val className = resolveInfo.activityInfo.name
            ProcessTextAppInfo(
                key = TextMenuConfig.getProcessTextItemKey(packageName, className),
                label = resolveInfo.loadLabel(context.packageManager).toString(),
                packageName = packageName,
                className = className
            )
        }.sortedBy { it.label }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 通用配置项行
 */
@Composable
fun ConfigItemRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onEditClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.text_menu_edit_title)
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
