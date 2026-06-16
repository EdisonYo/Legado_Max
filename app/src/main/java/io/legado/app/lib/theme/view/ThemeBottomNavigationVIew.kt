package io.legado.app.lib.theme.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.lib.theme.elevation

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    private val defaultElevation: Float = context.elevation
    private val defaultBgColor: Int = context.bottomBackground

    init {
        applyDefaultStyle()
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    /**
     * 应用默认主题样式（在 init 和 reset 时调用）
     */
    private fun applyDefaultStyle() {
        val transparentNavBar = context.transparentNavBar
        val bgColor = defaultBgColor
        if (transparentNavBar) {
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            setBackgroundColor(bgColor)
            elevation = defaultElevation
        }
        val textIsDark = ColorUtils.isColorLight(bgColor)
        val textColor = context.getSecondaryTextColor(textIsDark)
        val colorStateList = Selector.colorBuild()
            .setDefaultColor(textColor)
            .setSelectedColor(ThemeStore.accentColor(context))
            .create()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
        if (AppConfig.isEInkMode || transparentNavBar) {
            isItemHorizontalTranslationEnabled = false
            itemBackground = Color.TRANSPARENT.toDrawable()
        }
    }

    /**
     * 切换为玻璃/透明样式：背景完全透明、阴影去除
     *
     * Material BottomNavigationView 有三层背景机制：
     * 1. backgroundTint（surface color，会覆盖 setBackgroundColor）
     * 2. background（ColorDrawable）
     * 3. elevation（阴影）
     * 必须三层都清除才能真正透明
     */
    @Suppress("DEPRECATION")
    fun setGlassTransparent() {
        // 关闭 Material 自动添加的 surface 颜色 tint
        backgroundTintList = null
        // 设置透明背景
        setBackgroundColor(Color.TRANSPARENT)
        // 去除 elevation 阴影
        elevation = 0f
        // 清除 OutlineProvider 防止系统自动绘制阴影
        outlineProvider = null
        // 关闭 Material 主题的 item 选中态背景
        itemBackground = Color.TRANSPARENT.toDrawable()
        isItemHorizontalTranslationEnabled = false
    }

    /**
     * 恢复默认主题样式
     */
    fun resetToDefaultStyle() {
        backgroundTintList = null
        outlineProvider = android.view.ViewOutlineProvider.BOUNDS
        applyDefaultStyle()
    }

    fun addBadgeView(index: Int): BadgeView {
        //获取底部菜单view
        val menuView = getChildAt(0) as ViewGroup
        //获取第index个itemView
        val itemView = menuView.getChildAt(index) as ViewGroup
        val badgeBinding = ViewNavigationBadgeBinding.inflate(LayoutInflater.from(context))
        itemView.addView(badgeBinding.root)
        return badgeBinding.viewBadge
    }

}