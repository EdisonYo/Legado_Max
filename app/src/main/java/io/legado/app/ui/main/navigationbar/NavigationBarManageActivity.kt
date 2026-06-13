package io.legado.app.ui.main.navigationbar

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.NavigationBarConfig
import io.legado.app.data.entities.NavigationBarEntry
import io.legado.app.data.entities.Source
import io.legado.app.help.config.AppConfig
import io.legado.app.model.NavigationBarManager
import io.legado.app.ui.main.navigationbar.compose.EditPanel
import io.legado.app.ui.main.navigationbar.compose.SchemeCard
import io.legado.app.ui.main.navigationbar.compose.TabLayout
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.theme.setLegadoContent
import java.util.UUID

/**
 * 底栏管理界面
 */
class NavigationBarManageActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)

        setLegadoContent {
            NavigationBarManageScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBarManageScreen(
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf(loadPackages(selectedTab == 1)) }
    var activeDirName by remember { mutableStateOf(getActiveDirName(selectedTab == 1)) }
    var showEditPanel by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<NavigationBarEntry?>(null) }

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
