package io.legado.app.data.entities

data class NavigationBarConfig(
    var name: String,
    var isNightMode: Boolean,
    var layoutMode: LayoutMode = LayoutMode.FIXED,
    var materialMode: MaterialMode = MaterialMode.SOLID,
    var opacity: Int = 100,
    var borderColor: Int = 0x72E7EEF5.toInt(),
    var borderOpacity: Int = 100,
    var bookshelfIcon: TabIconConfig? = TabIconConfig(),
    var discoveryIcon: TabIconConfig? = TabIconConfig(),
    var rssIcon: TabIconConfig? = TabIconConfig(),
    var myIcon: TabIconConfig? = TabIconConfig()
) {
    /** 获取书架图标配置，null 安全 */
    val safeBookshelfIcon: TabIconConfig get() = bookshelfIcon ?: TabIconConfig()
    /** 获取发现图标配置，null 安全 */
    val safeDiscoveryIcon: TabIconConfig get() = discoveryIcon ?: TabIconConfig()
    /** 获取订阅图标配置，null 安全 */
    val safeRssIcon: TabIconConfig get() = rssIcon ?: TabIconConfig()
    /** 获取我的图标配置，null 安全 */
    val safeMyIcon: TabIconConfig get() = myIcon ?: TabIconConfig()
}

/**
 * 单个 Tab 的图标配置
 *
 * @param presetName 预设图标名称：default / linear / rounded
 * @param customIconPath 自定义图标文件路径（为空则使用预设图标）
 */
data class TabIconConfig(
    var presetName: String = "default",
    var customIconPath: String = ""
) {
    /** 是否使用自定义图标 */
    val isCustom: Boolean get() = customIconPath.isNotEmpty()
}

enum class LayoutMode {
    FIXED,
    FLOATING;

    val displayName: String
        get() = when (this) {
            FIXED -> "固定"
            FLOATING -> "悬浮"
        }
}

enum class MaterialMode {
    SOLID,
    GLASS,
    FROSTED;

    val displayName: String
        get() = when (this) {
            SOLID -> "实心"
            GLASS -> "玻璃"
            FROSTED -> "磨砂"
        }
}