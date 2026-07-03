package io.legado.app.help.storage

import io.legado.app.data.appDb
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import io.legado.app.ui.book.read.config.HighlightRuleStore
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File

object BackupInfoHelper {

    data class BackupFileInfo(
        val fileName: String,
        val displayName: String,
        val size: Long = 0L,
        val selected: Boolean = true
    )

    data class BackupOverview(
        val items: List<BackupFileInfo>,
        val totalSize: Long = 0L,
        val selectedSize: Long = 0L
    )

    data class CategoryInfo(
        val name: String,
        val icon: String,
        val items: List<BackupFileInfo>,
        val totalSize: Long = 0L
    )

    private data class CategoryDef(
        val name: String,
        val icon: String,
        val keywords: List<String>
    )

    private val categoryConfig = listOf(
        CategoryDef(
            "书籍相关",
            "📚",
            listOf("bookshelf", "bookmark", "bookGroup", "readRecord", "bookCache", "bookChapterCache")
        ),
        CategoryDef(
            "源相关",
            "📗",
            listOf("bookSource", "rssSource", "rssStar", "sourceSub", "runtimeSourceCache")
        ),
        CategoryDef(
            "规则相关",
            "🔧",
            listOf("replaceRule", "txtTocRule", "dictRule", "keyboardAssist", "highlightRule")
        ),
        CategoryDef("语音相关", "🔊", listOf("httpTTS")),
        CategoryDef(
            "配置相关",
            "⚙️",
            listOf("config", "videoConfig", "readConfig", "shareConfig", "coverRule", "servers", "directLink")
        )
    )

    val displayNameMap = mapOf(
        "zip" to "打包备份文件",
        "unzipBackup" to "解压备份文件",
        "copyBackup" to "保存备份文件",
        "webDavBackup" to "上传 WebDAV",
        "clearBackupCache" to "清理临时文件",
        "webDavBackgroundImages" to "上传背景图片",
        "applyRestoreConfig" to "应用恢复配置",
        "themeBackgroundImages" to "主题背景图片",
        CoverGalleryRepository.backupDirName to "封面图集",
        "bookshelf.json" to "书架",
        "bookmark.json" to "书签",
        "bookGroup.json" to "书籍分组",
        "bookSource.json" to "书源",
        "rssSources.json" to "订阅源",
        "rssStar.json" to "订阅收藏",
        "sourceSub.json" to "源订阅",
        "replaceRule.json" to "替换规则",
        HighlightRuleStore.backupFileName to "高亮规则",
        "readRecord.json" to "阅读记录",
        "readRecordDetail.json" to "阅读详情",
        "readRecordSession.json" to "阅读时段",
        "searchHistory.json" to "搜索历史",
        "txtTocRule.json" to "TXT 目录规则",
        "httpTTS.json" to "TTS 配置",
        "keyboardAssists.json" to "键盘辅助",
        "dictRule.json" to "词典规则",
        "servers.json" to "服务器配置",
        "runtimeSourceCache.json" to "书源运行数据",
        "book_cache" to "书籍缓存",
        "bookCacheIndex.json" to "书籍缓存索引",
        "bookCacheBooks.json" to "书籍缓存书架信息",
        "bookChapterCache.json" to "书籍章节目录",
        ReadBookConfig.configFileName to "阅读样式配置",
        ReadBookConfig.shareConfigFileName to "共享阅读配置",
        ThemeConfig.configFileName to "主题配置",
        BookCover.configFileName to "封面规则",
        DirectLinkUpload.ruleFileName to "直链上传配置",
        "backgroundImages" to "阅读背景",
        "config.xml" to "应用配置",
        "videoConfig.xml" to "视频配置"
    )

    fun getDisplayName(fileName: String): String {
        return displayNameMap[fileName] ?: fileName
    }

    private val selectorFileAliases = mapOf(
        ReadBookConfig.shareConfigFileName to "readShareConfig.json",
        DirectLinkUpload.ruleFileName to "directLinkRule.json",
        BookCover.configFileName to "coverRule.json",
        CoverGalleryRepository.backupDirName to CoverGalleryRepository.backupDirName
    )

    fun getBackupOverview(): BackupOverview {
        return buildOverview(::isFileSelectedForBackup)
    }

    fun getRestoreOverview(): BackupOverview {
        return buildOverview(::isFileEnabledForRestore)
    }

    private fun buildOverview(isSelected: (String) -> Boolean): BackupOverview {
        val items = mutableListOf<BackupFileInfo>()
        var totalSize = 0L
        var selectedSize = 0L

        fun addItem(fileName: String, size: Long = 0L) {
            val selected = isSelected(fileName)
            totalSize += size
            if (selected) selectedSize += size
            items.add(
                BackupFileInfo(
                    fileName = fileName,
                    displayName = displayNameMap[fileName] ?: fileName,
                    size = size,
                    selected = selected
                )
            )
        }

        val dbItems = listOf(
            "bookshelf.json",
            "bookmark.json",
            "bookGroup.json",
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "sourceSub.json",
            "replaceRule.json",
            "readRecord.json",
            "readRecordDetail.json",
            "readRecordSession.json",
            "searchHistory.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "servers.json"
        )
        dbItems.forEach { addItem(it) }

        addItem("runtimeSourceCache.json")
        addItem(HighlightRuleStore.backupFileName)
        addBookCacheItems(::addItem)
        addConfigItems(::addItem)
        addBackgroundItems(::addItem)
        addCoverGalleryItem(::addItem)

        return BackupOverview(items, totalSize, selectedSize)
    }

    private fun addBookCacheItems(addItem: (String, Long) -> Unit) {
        val selectedBooks = BookCacheSelectorConfig.getSelectedBooks()
        val cacheDir = File(BookHelp.cachePath)
        var bookCacheSize = 0L
        if (cacheDir.exists()) {
            selectedBooks.forEach { book ->
                val bookFolder = File(cacheDir, book.getFolderName())
                if (bookFolder.exists()) {
                    bookCacheSize += bookFolder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
            }
        }
        addItem("book_cache", bookCacheSize)
    }

    private fun addConfigItems(addItem: (String, Long) -> Unit) {
        listOf(
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfig.configFileName,
            BookCover.configFileName,
            "config.xml",
            "videoConfig.xml"
        ).forEach { fileName ->
            val file = File(appCtx.filesDir, fileName)
            addItem(fileName, if (file.exists()) file.length() else 0L)
        }

        val directLinkConfig = DirectLinkUpload.getConfig()
        val directLinkSize = directLinkConfig?.let { GSON.toJson(it).length.toLong() } ?: 0L
        addItem(DirectLinkUpload.ruleFileName, directLinkSize)
    }

    private fun addBackgroundItems(addItem: (String, Long) -> Unit) {
        val totalBgSize = Backup.getBackgroundImageFiles()
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
        addItem("backgroundImages", totalBgSize)
    }

    private fun addCoverGalleryItem(addItem: (String, Long) -> Unit) {
        val imageSize = appDb.coverGalleryDao.allImages.asSequence()
            .map { File(it.path) }
            .filter { it.exists() && it.isFile }
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
        val estimatedDataSize = appDb.coverGalleryDao.allGroups.size * 100L
        addItem(CoverGalleryRepository.backupDirName, imageSize + estimatedDataSize)
    }

    private fun isFileSelectedForBackup(fileName: String): Boolean {
        return when (fileName) {
            "book_cache",
            "bookChapterCache.json",
            "bookCacheBooks.json",
            "bookCacheIndex.json" -> BackupSelectorConfig.isSelected("bookCache")
            "backgroundImages" -> BackupSelectorConfig.isSelected("backgroundImages")
            else -> {
                val key = BackupSelectorConfig.allItems.find {
                    it.fileName == fileName || it.fileName == selectorFileAliases[fileName]
                }?.key ?: return true
                BackupSelectorConfig.isSelected(key)
            }
        }
    }

    private fun isFileEnabledForRestore(fileName: String): Boolean {
        return when (fileName) {
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            "backgroundImages" -> !BackupConfig.ignoreReadConfig
            "book_cache",
            "bookChapterCache.json",
            "bookCacheBooks.json",
            "bookCacheIndex.json" -> !BackupConfig.ignoreBookCache
            else -> true
        }
    }

    fun categorizeItems(items: List<BackupFileInfo>, onlySelected: Boolean = false): List<CategoryInfo> {
        val filteredItems = if (onlySelected) items.filter { it.selected } else items
        val result = mutableListOf<CategoryInfo>()
        val assigned = mutableSetOf<String>()

        for (cfg in categoryConfig) {
            val matched = filteredItems.filter { item ->
                cfg.keywords.any { kw ->
                    item.fileName.lowercase().contains(kw.lowercase())
                } && !assigned.contains(item.fileName)
            }
            if (matched.isNotEmpty()) {
                matched.forEach { assigned.add(it.fileName) }
                result.add(
                    CategoryInfo(
                        name = cfg.name,
                        icon = cfg.icon,
                        items = matched,
                        totalSize = matched.sumOf { it.size }
                    )
                )
            }
        }

        val themeItems = filteredItems.filter {
            !assigned.contains(it.fileName) &&
                (it.fileName == "backgroundImages" || it.fileName == ThemeConfig.configFileName)
        }
        if (themeItems.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "主题",
                    icon = "🎨",
                    items = themeItems,
                    totalSize = themeItems.sumOf { it.size }
                )
            )
            themeItems.forEach { assigned.add(it.fileName) }
        }

        val remaining = filteredItems.filter { !assigned.contains(it.fileName) }
        if (remaining.isNotEmpty()) {
            result.add(
                CategoryInfo(
                    name = "其他",
                    icon = "📦",
                    items = remaining,
                    totalSize = remaining.sumOf { it.size }
                )
            )
        }

        return result
    }

    fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }

    fun getItemCount(key: String): Int {
        return when (key) {
            "bookshelf" -> appDb.bookDao.allBookCount
            "bookmark" -> appDb.bookmarkDao.count
            "bookGroup" -> appDb.bookGroupDao.count
            "bookSource" -> appDb.bookSourceDao.allCount()
            "rssSources" -> appDb.rssSourceDao.size
            "rssStar" -> appDb.rssStarDao.count
            "sourceSub" -> appDb.ruleSubDao.count
            "replaceRule" -> appDb.replaceRuleDao.count
            "readRecord" -> appDb.readRecordDao.count
            "readRecordDetail" -> appDb.readRecordDao.getDetailsCount()
            "searchHistory" -> appDb.searchKeywordDao.count
            "txtTocRule" -> appDb.txtTocRuleDao.count
            "httpTTS" -> appDb.httpTTSDao.count
            "keyboardAssists" -> appDb.keyboardAssistsDao.count
            "dictRule" -> appDb.dictRuleDao.count
            "servers" -> appDb.serverDao.count
            "runtimeSourceCache" -> appDb.cacheDao.getRuntimeSourceCacheCount(System.currentTimeMillis())
            else -> 0
        }
    }
}