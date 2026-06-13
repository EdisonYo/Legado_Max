package io.legado.app.model

import io.legado.app.constant.EventBus
import io.legado.app.data.entities.LayoutMode
import io.legado.app.data.entities.MaterialMode
import io.legado.app.data.entities.NavigationBarConfig
import io.legado.app.data.entities.NavigationBarEntry
import io.legado.app.data.entities.Source
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 底栏液态玻璃效果方案管理器
 *
 * 提供方案的创建、加载、保存、删除和应用功能。
 * 方案存储在 SharedPreferences 中，使用 JSON 格式序列化。
 */
object NavigationBarManager {

    /** SharedPreferences 键名前缀 */
    private const val PREFIX = "navigationBar_"

    /**
     * 创建默认方案
     *
     * 默认方案使用固定布局、实心材质，适用于日间或夜间模式。
     * dirName 固定为 "default"，来源标记为 BUILTIN（内置）。
     */
    fun defaultEntry(isNight: Boolean): NavigationBarEntry {
        return NavigationBarEntry(
            NavigationBarConfig(
                name = "默认",
                isNightMode = isNight,
                layoutMode = LayoutMode.FIXED,
                materialMode = MaterialMode.SOLID,
                opacity = 100,
                borderColor = 0x72E7EEF5.toInt(),
                borderOpacity = 100
            ),
            Source.BUILTIN,
            "default"
        )
    }

    /**
     * 加载指定模式的所有方案
     *
     * 返回列表始终包含默认方案作为第一个元素，后跟用户保存的自定义方案。
     * 只返回与指定模式（日间/夜间）匹配的方案。
     * 过滤掉 dirName="default" 的 SP 条目，避免与内置默认方案重复。
     */
    fun loadEntries(isNight: Boolean): List<NavigationBarEntry> {
        val entries = mutableListOf<NavigationBarEntry>()
        entries.add(defaultEntry(isNight))

        val keys = appCtx.defaultSharedPreferences.all.keys
        keys.filter { it.startsWith(PREFIX) }.forEach { key ->
            val json = appCtx.getPrefString(key)
            json?.let {
                val entry = GSON.fromJsonObject<NavigationBarEntry>(it).getOrNull()
                if (entry != null && entry.config.isNightMode == isNight && entry.dirName != "default") {
                    entries.add(entry)
                }
            }
        }

        return entries
    }

    /**
     * 加载单个方案
     *
     * 如果 dirName 为 "default"，返回当前主题模式对应的默认方案。
     * 否则从 SharedPreferences 加载指定名称的方案。
     */
    fun loadEntry(dirName: String): NavigationBarEntry? {
        if (dirName == "default") {
            return defaultEntry(AppConfig.isNightTheme)
        }

        val json = appCtx.getPrefString(PREFIX + dirName)
        if (json == null) return null

        return GSON.fromJsonObject<NavigationBarEntry>(json).getOrNull()
    }

    /**
     * 保存方案
     *
     * 检查方案名称的唯一性（同一模式下不允许重复名称）。
     * 默认方案（dirName="default"）不允许保存到 SP。
     */
    fun saveEntry(entry: NavigationBarEntry) {
        // 默认方案不允许保存到 SP（它是内置的，由 defaultEntry() 动态生成）
        if (entry.dirName == "default") {
            appCtx.toastOnUi("默认方案不可修改")
            return
        }

        val existingEntries = loadEntries(entry.config.isNightMode)
        if (existingEntries.any { it.config.name == entry.config.name && it.dirName != entry.dirName }) {
            appCtx.toastOnUi("方案名称已存在，请使用其他名称")
            return
        }

        val json = GSON.toJson(entry)
        appCtx.putPrefString(PREFIX + entry.dirName, json)
    }

    /**
     * 删除方案
     *
     * 默认方案（dirName="default"）不可删除。
     */
    fun deleteEntry(dirName: String) {
        if (dirName == "default") {
            appCtx.toastOnUi("默认方案不可删除")
            return
        }

        appCtx.removePref(PREFIX + dirName)
    }

    /**
     * 应用方案
     *
     * 将方案记录为当前激活方案（根据模式更新 AppConfig），
     * 并发送 EventBus 事件通知界面刷新底栏效果。
     */
    fun apply(entry: NavigationBarEntry) {
        val config = entry.config

        if (config.isNightMode) {
            AppConfig.activeNavigationBarNight = entry.dirName
        } else {
            AppConfig.activeNavigationBarDay = entry.dirName
        }

        postEvent(EventBus.NAVIGATION_BAR_CHANGED, config.isNightMode)
    }
}
