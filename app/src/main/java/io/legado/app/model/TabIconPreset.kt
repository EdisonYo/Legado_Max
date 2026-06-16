package io.legado.app.model

import io.legado.app.R
import io.legado.app.data.entities.TabIconConfig

/**
 * Tab 图标预设映射工具
 *
 * 管理预设图标名称到 drawable 资源 ID 的映射关系。
 * 目前仅保留默认预设，自定义图标通过 TabIconConfig.customIconPath 实现。
 */
object TabIconPreset {

    /** 预设图标名称列表 */
    val PRESET_NAMES = listOf("default")

    /** 预设显示名称 */
    val PRESET_DISPLAY_NAMES = mapOf(
        "default" to "默认"
    )

    /** 每个 tab 对应的默认 selector drawable 资源映射（用于 BottomNavigationView） */
    private val DEFAULT_DRAWABLES = mapOf(
        "bookshelf" to R.drawable.ic_bottom_books,
        "discovery" to R.drawable.ic_bottom_explore,
        "rss" to R.drawable.ic_bottom_rss_feed,
        "my" to R.drawable.ic_bottom_person
    )

    /** 每个 tab 对应的默认选中态 VectorDrawable 资源映射（用于 Compose painterResource 预览） */
    private val DEFAULT_PREVIEW_DRAWABLES = mapOf(
        "bookshelf" to R.drawable.ic_bottom_books_s,
        "discovery" to R.drawable.ic_bottom_explore_s,
        "rss" to R.drawable.ic_bottom_rss_feed_s,
        "my" to R.drawable.ic_bottom_person_s
    )

    /** tab 键名列表 */
    val TAB_KEYS = listOf("bookshelf", "discovery", "rss", "my")

    /** tab 显示名称 */
    val TAB_DISPLAY_NAMES = mapOf(
        "bookshelf" to "书架",
        "discovery" to "发现",
        "rss" to "订阅",
        "my" to "我的"
    )

    /**
     * 获取指定 tab 和图标配置对应的 drawable 资源 ID
     *
     * @param tabKey tab 键名：bookshelf / discovery / rss / my
     * @param iconConfig 图标配置
     * @return drawable 资源 ID（selector，用于 BottomNavigationView），如果配置了自定义图标则返回 null
     */
    fun getDrawableResId(tabKey: String, iconConfig: TabIconConfig): Int? {
        if (iconConfig.isCustom) return null
        return DEFAULT_DRAWABLES[tabKey]
    }

    /**
     * 获取指定 tab 的默认 selector drawable 资源 ID（用于 BottomNavigationView）
     */
    fun getPresetDrawableResId(tabKey: String, presetName: String): Int {
        return DEFAULT_DRAWABLES[tabKey] ?: R.drawable.ic_bottom_books
    }

    /**
     * 获取指定 tab 的默认选中态 VectorDrawable 资源 ID（用于 Compose painterResource 预览）
     *
     * Compose 的 painterResource 只支持 VectorDrawable 和位图，不支持 StateListDrawable（selector），
     * 因此预览时需要使用选中态的单个矢量图标。
     */
    fun getPreviewDrawableResId(tabKey: String, presetName: String): Int {
        return DEFAULT_PREVIEW_DRAWABLES[tabKey] ?: R.drawable.ic_bottom_books_s
    }

    /**
     * 获取预设的显示名称
     */
    fun getPresetDisplayName(presetName: String): String {
        return PRESET_DISPLAY_NAMES[presetName] ?: presetName
    }
}
