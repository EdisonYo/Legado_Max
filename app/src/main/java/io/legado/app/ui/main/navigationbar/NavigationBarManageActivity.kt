package io.legado.app.ui.main.navigationbar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.NavigationBarConfig
import io.legado.app.data.entities.NavigationBarEntry
import io.legado.app.data.entities.Source
import io.legado.app.data.entities.TabIconConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.model.NavigationBarManager
import io.legado.app.ui.main.navigationbar.compose.EditPanel
import io.legado.app.ui.main.navigationbar.compose.SchemeCard
import io.legado.app.ui.main.navigationbar.compose.TabLayout
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.theme.setLegadoContent
import java.io.File
import java.util.UUID

/**
 * 底栏管理界面
 */
class NavigationBarManageActivity : androidx.appcompat.app.AppCompatActivity() {

    /** 当前等待选择自定义图标的 tab 键名 */
    private var pendingCustomIconTabKey by mutableStateOf<String?>(null)

    /** 文件选择器 */
    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val tabKey = pendingCustomIconTabKey ?: return@registerForActivityResult
        if (uri != null) {
            val savedPath = copyIconToInternal(uri, tabKey)
            if (savedPath != null) {
                // 更新编辑中的 entry 的图标配置
                _editingEntry?.let { entry ->
                    val newIconConfig = TabIconConfig(
                        presetName = entry.config.getIconConfig(tabKey).presetName,
                        customIconPath = savedPath
                    )
                    val newConfig = entry.config.setIconConfig(tabKey, newIconConfig)
                    _editingEntry = entry.copy(config = newConfig)
                }
            }
        }
        pendingCustomIconTabKey = null
    }

    /** 编辑中的 entry（供文件选择回调和 Compose 同步使用） */
    internal var _editingEntry by mutableStateOf<NavigationBarEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)

        setLegadoContent {
            NavigationBarManageScreen(
                activity = this@NavigationBarManageActivity,
                onBackClick = { finish() }
            )
        }
    }

    /**
     * 启动文件选择器选择自定义图标
     */
    fun pickCustomIcon(tabKey: String) {
        pendingCustomIconTabKey = tabKey
        iconPickerLauncher.launch(arrayOf("image/*"))
    }

    /**
     * 将选中的图标文件复制到应用内部存储
     *
     * @return 保存的文件绝对路径，失败返回 null
     */
    private fun copyIconToInternal(uri: Uri, tabKey: String): String? {
        return try {
            val dir = File(filesDir, "nav_icons")
            if (!dir.exists()) dir.mkdirs()

            val entryDirName = _editingEntry?.dirName ?: "unknown"
            val entryDir = File(dir, entryDirName)
            if (!entryDir.exists()) entryDir.mkdirs()

            val destFile = File(entryDir, "${tabKey}_icon.png")
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * NavigationBarConfig 的图标配置辅助扩展
 */
private fun NavigationBarConfig.getIconConfig(tabKey: String): TabIconConfig {
    return when (tabKey) {
        "bookshelf" -> safeBookshelfIcon
        "discovery" -> safeDiscoveryIcon
        "rss" -> safeRssIcon
        "my" -> safeMyIcon
        else -> TabIconConfig()
    }
}

private fun NavigationBarConfig.setIconConfig(tabKey: String, iconConfig: TabIconConfig): NavigationBarConfig {
    return when (tabKey) {
        "bookshelf" -> copy(bookshelfIcon = iconConfig)
        "discovery" -> copy(discoveryIcon = iconConfig)
        "rss" -> copy(rssIcon = iconConfig)
        "my" -> copy(myIcon = iconConfig)
        else -> this
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBarManageScreen(
    activity: NavigationBarManageActivity,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf(loadPackages(selectedTab == 1)) }
    var activeDirName by remember { mutableStateOf(getActiveDirName(selectedTab == 1)) }
    var showEditPanel by remember { mutableStateOf(false) }
    // 使用 Activity 的 _editingEntry 作为状态源，确保文件选择回调能更新
    var editingEntry by remember { mutableStateOf<NavigationBarEntry?>(null) }

    // 同步 Activity 的 _editingEntry 到 Compose 状态
    val activityEntry = activity._editingEntry
    if (activityEntry != null && activityEntry != editingEntry) {
        editingEntry = activityEntry
    }

    fun refreshEntries() {
        val isNight = selectedTab == 1
        entries = loadPackages(isNight)
        activeDirName = getActiveDirName(isNight)
    }

    val topBarColor = pageTopBarContainerColor()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    Text(
                        text = "底栏管理",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val isNight = selectedTab == 1
                        val newConfig = NavigationBarConfig(
                            name = "新方案",
                            isNightMode = isNight
                        )
                        val newEntry = NavigationBarEntry(
                            config = newConfig,
                            source = Source.LOCAL,
                            dirName = UUID.randomUUID().toString()
                        )
                        editingEntry = newEntry
                        showEditPanel = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color.Transparent
        ) {
            if (showEditPanel && editingEntry != null) {
                EditPanel(
                    config = editingEntry!!.config,
                    isNightMode = editingEntry!!.config.isNightMode,
                    onConfigChange = { newConfig ->
                        editingEntry = editingEntry!!.copy(config = newConfig)
                    },
                    onSave = {
                        savePackage(editingEntry!!)
                        showEditPanel = false
                        editingEntry = null
                        refreshEntries()
                    },
                    onCancel = {
                        showEditPanel = false
                        editingEntry = null
                    },
                    onPickCustomIcon = { tabKey ->
                        activity._editingEntry = editingEntry
                        activity.pickCustomIcon(tabKey)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TabLayout(
                        selectedTab = selectedTab,
                        onTabChange = { newTab ->
                            selectedTab = newTab
                            refreshEntries()
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        content = {
                            items(entries) { entry ->
                                SchemeCard(
                                    entry = entry,
                                    isActive = entry.dirName == activeDirName,
                                    onClick = {
                                        applyPackage(entry)
                                        activeDirName = entry.dirName
                                    },
                                    onEdit = {
                                        showEditDialog(entry)
                                        editingEntry = entry
                                        showEditPanel = true
                                    },
                                    onDelete = {
                                        deletePackage(entry)
                                        refreshEntries()
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun loadPackages(isNight: Boolean): List<NavigationBarEntry> {
    return NavigationBarManager.loadEntries(isNight)
}

private fun getActiveDirName(isNight: Boolean): String {
    return AppConfig.activeDirName(isNight)
}

private fun applyPackage(entry: NavigationBarEntry) {
    NavigationBarManager.apply(entry)
}

private fun showEditDialog(entry: NavigationBarEntry) {}

private fun deletePackage(entry: NavigationBarEntry) {
    if (entry.source == Source.BUILTIN) return
    NavigationBarManager.deleteEntry(entry.dirName)
}

private fun savePackage(entry: NavigationBarEntry) {
    NavigationBarManager.saveEntry(entry)
}
