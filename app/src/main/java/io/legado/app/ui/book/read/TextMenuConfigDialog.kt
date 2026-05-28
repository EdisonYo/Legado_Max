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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.toastOnUi

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
    
    var hiddenIds by remember { mutableStateOf(TextMenuConfig.getHiddenMenuItemIds(context)) }
    var hiddenProcessItems by remember { mutableStateOf(TextMenuConfig.getHiddenProcessTextItems(context)) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val processTextApps = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getProcessTextApps(context)
        } else {
            emptyList()
        }
    }
    
    val showTabs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && processTextApps.isNotEmpty()

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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (selectedTab == 0) {
                        items(menuItems) { item ->
                            MenuItemRow(
                                item = item,
                                isChecked = item.id !in hiddenIds,
                                onCheckedChange = { checked ->
                                    hiddenIds = hiddenIds.toMutableSet().apply {
                                        if (checked) remove(item.id) else add(item.id)
                                    }
                                }
                            )
                        }
                    } else {
                        items(processTextApps) { appInfo ->
                            ProcessTextAppRow(
                                appInfo = appInfo,
                                isChecked = appInfo.key !in hiddenProcessItems,
                                onCheckedChange = { checked ->
                                    hiddenProcessItems = hiddenProcessItems.toMutableSet().apply {
                                        if (checked) remove(appInfo.key) else add(appInfo.key)
                                    }
                                }
                            )
                        }
                    }
                }

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
                        }
                    ) {
                        Text(text = stringResource(R.string.reset_to_default))
                    }

                    TextButton(
                        onClick = {
                            TextMenuConfig.setHiddenMenuItemIds(context, hiddenIds)
                            TextMenuConfig.setHiddenProcessTextItems(context, hiddenProcessItems)
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

data class ProcessTextAppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val className: String
)

@Suppress("DEPRECATION")
private fun getProcessTextApps(context: Context): List<ProcessTextAppInfo> {
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

@Composable
fun MenuItemRow(
    item: TextMenuConfig.MenuItemInfo,
    isChecked: Boolean,
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
                text = stringResource(item.nameResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ID: ${item.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ProcessTextAppRow(
    appInfo: ProcessTextAppInfo,
    isChecked: Boolean,
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
                text = appInfo.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
