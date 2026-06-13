package io.legado.app.model

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.qmdeve.liquidglass.widget.LiquidGlassView
import io.legado.app.R
import io.legado.app.data.entities.LayoutMode
import io.legado.app.data.entities.MaterialMode
import io.legado.app.data.entities.NavigationBarConfig
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.utils.dpToPx

/**
 * 底栏液态玻璃效果应用器
 *
 * 采用动态叠加方案：
 * - FIXED 模式：不修改底栏任何属性，保持原始默认样子
 * - FLOATING 模式：底栏背景透明 + 添加 margin + 叠加效果层
 *
 * 叠加层 z-order（从下到上）：
 * 1. LinearLayout — ViewPager + 底栏图标（背景透明）
 * 2. LiquidGlassView — 折射/模糊渲染（绑定 ViewPager 作为采样源）
 * 3. overlay View — 色调 + 高光 + 边框
 *
 * 关键：LiquidGlassView 和 overlay 放在 LinearLayout 上面，
 * isClickable=false 保证触摸事件穿透到底栏图标。
 * LiquidGlassView 绑定 ViewPager（内容区域），
 * 这样玻璃效果才能折射/模糊底栏后面的内容。
 */
object NavigationBarEffectApplier {

    private const val TAG_OVERLAY = "navigation_bar_overlay"
    private const val TAG_GLASS_VIEW = "navigation_bar_glass_view"

    /** 保存底栏原始背景（深拷贝） */
    private var originalBackground: android.graphics.drawable.Drawable? = null
    /** 保存底栏容器（LinearLayout）原始背景 */
    private var originalContainerBackground: android.graphics.drawable.Drawable? = null
    /** 保存底栏原始 margin（值拷贝，非引用） */
    private var originalNavMargins: IntArray? = null

    fun applyEffect(config: NavigationBarConfig, binding: ActivityMainBinding) {
        if (config.materialMode == MaterialMode.SOLID) {
            removeOverlay(binding)
            return
        }

        // FIXED 模式：不修改底栏，保持原始默认样子
        if (config.layoutMode == LayoutMode.FIXED) {
            removeOverlay(binding)
            return
        }

        // FLOATING 模式：应用玻璃效果
        val useFallback = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        if (useFallback) {
            addOverlayOnly(binding, config)
        } else {
            addOverlay(binding, config)
        }
    }

    private fun addOverlay(
        binding: ActivityMainBinding,
        config: NavigationBarConfig
    ) {
        val rootView = binding.root as? ViewGroup ?: return

        val existingGlass = rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)
        val existingOverlay = rootView.findViewWithTag<View>(TAG_OVERLAY)

        if (existingGlass != null && existingOverlay != null) {
            updateOverlay(existingGlass, existingOverlay, binding, config)
            return
        }

        existingGlass?.let { rootView.removeView(it) }
        existingOverlay?.let { rootView.removeView(it) }

        val navView = binding.bottomNavigationView

        saveOriginalState(navView)

        // 底栏图标背景透明
        navView.background = null

        // 底栏容器（LinearLayout）背景也透明
        val container = navView.parent as? ViewGroup
        if (container != null && originalContainerBackground == null) {
            originalContainerBackground = container.background?.constantState?.newDrawable()?.mutate()
                ?: container.background
        }
        container?.background = null

        // 悬浮模式：给底栏加 margin
        val margin = 16.dpToPx()
        val lp = navView.layoutParams as? LinearLayout.LayoutParams
        if (lp != null) {
            lp.setMargins(margin, 0, margin, margin)
            navView.layoutParams = lp
        }

        // z-order: 追加到 FrameLayout 末尾 = 最上层
        // LinearLayout 在 index 0（最底层）
        // LiquidGlassView 在 index 1（中间层，折射/模糊）
        // overlay 在 index 2（最上层，色调+边框）
        // isClickable=false 保证触摸事件穿透到底栏图标
        val glassView = createGlassView(navView, binding, config)
        rootView.addView(glassView)

        val overlay = createOverlayView(navView, config)
        rootView.addView(overlay)

        applyMargins(overlay, glassView, margin)
    }

    private fun addOverlayOnly(binding: ActivityMainBinding, config: NavigationBarConfig) {
        val rootView = binding.root as? ViewGroup ?: return

        rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)?.let {
            rootView.removeView(it)
        }

        val existingOverlay = rootView.findViewWithTag<View>(TAG_OVERLAY)
        val navView = binding.bottomNavigationView

        saveOriginalState(navView)
        navView.background = null

        val container = navView.parent as? ViewGroup
        if (container != null && originalContainerBackground == null) {
            originalContainerBackground = container.background?.constantState?.newDrawable()?.mutate()
                ?: container.background
        }
        container?.background = null

        val margin = 16.dpToPx()
        val lp = navView.layoutParams as? LinearLayout.LayoutParams
        if (lp != null) {
            lp.setMargins(margin, 0, margin, margin)
            navView.layoutParams = lp
        }

        if (existingOverlay != null) {
            updateOverlayView(existingOverlay, navView, config)
            val overlayLp = existingOverlay.layoutParams as? FrameLayout.LayoutParams
            if (overlayLp != null) {
                overlayLp.setMargins(margin, 0, margin, margin)
                existingOverlay.layoutParams = overlayLp
            }
        } else {
            val overlay = createOverlayView(navView, config)
            rootView.addView(overlay)
            val overlayLp = overlay.layoutParams as? FrameLayout.LayoutParams
            if (overlayLp != null) {
                overlayLp.setMargins(margin, 0, margin, margin)
                overlay.layoutParams = overlayLp
            }
        }
    }

    private fun saveOriginalState(navView: View) {
        if (originalBackground == null) {
            originalBackground = navView.background?.constantState?.newDrawable()?.mutate()
                ?: navView.background
        }
        if (originalNavMargins == null) {
            val lp = navView.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                originalNavMargins = intArrayOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
            }
        }
    }

    private fun createOverlayView(navView: View, config: NavigationBarConfig): View {
        return View(navView.context).apply {
            tag = TAG_OVERLAY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            isClickable = false
            isFocusable = false
            navView.post {
                val navHeight = navView.height
                if (navHeight > 0) {
                    layoutParams.height = navHeight
                    this.layoutParams = layoutParams
                }
            }
            background = createOverlayDrawable(config)
        }
    }

    private fun createOverlayDrawable(config: NavigationBarConfig): GradientDrawable {
        val cornerRadius = 24f.dpToPx().toFloat()

        val (topAlpha, bottomAlpha) = when (config.materialMode) {
            MaterialMode.GLASS -> {
                val opacityFactor = config.opacity / 100f
                val top = (0.35f * opacityFactor * 255).toInt().coerceIn(0, 255)
                val bottom = (0.12f * opacityFactor * 255).toInt().coerceIn(0, 255)
                Pair(top, bottom)
            }
            MaterialMode.FROSTED -> {
                val opacityFactor = config.opacity / 100f
                val top = (0.70f * opacityFactor * 255).toInt().coerceIn(0, 255)
                val bottom = (0.50f * opacityFactor * 255).toInt().coerceIn(0, 255)
                Pair(top, bottom)
            }
            else -> Pair(0, 0)
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius

            colors = intArrayOf(
                (topAlpha shl 24) or 0xFFFFFF,
                (bottomAlpha shl 24) or 0xFFFFFF
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM

            val borderAlpha = (config.borderOpacity / 100f * 255).toInt()
            val borderColorWithAlpha = (borderAlpha shl 24) or (config.borderColor and 0x00FFFFFF)
            setStroke(2.dpToPx(), borderColorWithAlpha)
        }
    }

    private fun createGlassView(
        navView: View,
        binding: ActivityMainBinding,
        config: NavigationBarConfig
    ): LiquidGlassView {
        return LiquidGlassView(navView.context).apply {
            tag = TAG_GLASS_VIEW
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            isClickable = false
            isFocusable = false
            navView.post {
                val navHeight = navView.height
                if (navHeight > 0) {
                    layoutParams.height = navHeight
                    this.layoutParams = layoutParams
                }
            }
            setupGlassView(this, binding, config)
        }
    }

    private fun updateOverlay(
        glassView: LiquidGlassView,
        overlay: View,
        binding: ActivityMainBinding,
        config: NavigationBarConfig
    ) {
        setupGlassView(glassView, binding, config)
        updateOverlayView(overlay, binding.bottomNavigationView, config)

        val margin = 16.dpToPx()

        val navLp = binding.bottomNavigationView.layoutParams as? LinearLayout.LayoutParams
        if (navLp != null) {
            navLp.setMargins(margin, 0, margin, margin)
            binding.bottomNavigationView.layoutParams = navLp
        }

        applyMargins(overlay, glassView, margin)
    }

    private fun applyMargins(overlay: View, glassView: LiquidGlassView, margin: Int) {
        val overlayLp = overlay.layoutParams as? FrameLayout.LayoutParams
        if (overlayLp != null) {
            overlayLp.setMargins(margin, 0, margin, margin)
            overlay.layoutParams = overlayLp
        }
        val glassLp = glassView.layoutParams as? FrameLayout.LayoutParams
        if (glassLp != null) {
            glassLp.setMargins(margin, 0, margin, margin)
            glassView.layoutParams = glassLp
        }
    }

    private fun updateOverlayView(overlay: View, navView: View, config: NavigationBarConfig) {
        overlay.background = createOverlayDrawable(config)
        navView.post {
            val navHeight = navView.height
            if (navHeight > 0) {
                overlay.layoutParams.height = navHeight
                overlay.layoutParams = overlay.layoutParams
            }
        }
    }

    private fun removeOverlay(binding: ActivityMainBinding) {
        val rootView = binding.root as? ViewGroup ?: return
        val navView = binding.bottomNavigationView

        rootView.findViewWithTag<LiquidGlassView>(TAG_GLASS_VIEW)?.let {
            rootView.removeView(it)
        }

        rootView.findViewWithTag<View>(TAG_OVERLAY)?.let {
            rootView.removeView(it)
        }

        // 恢复底栏图标原始背景
        originalBackground?.let {
            navView.background = it
        }

        // 恢复底栏容器原始背景
        val container = navView.parent as? ViewGroup
        originalContainerBackground?.let {
            container?.background = it
        }

        // 恢复底栏原始 margin
        originalNavMargins?.let { margins ->
            val lp = navView.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                lp.setMargins(margins[0], margins[1], margins[2], margins[3])
                navView.layoutParams = lp
            }
        }
    }

    /**
     * 配置 LiquidGlassView
     *
     * 绑定 ViewPager（内容区域）作为采样源，
     * 这样玻璃效果才能折射/模糊底栏后面的内容。
     */
    private fun setupGlassView(
        glassView: LiquidGlassView,
        binding: ActivityMainBinding,
        config: NavigationBarConfig
    ) {
        try {
            glassView.visibility = View.VISIBLE

            // 绑定 ViewPager 作为采样源
            val viewPager = binding.viewPagerMain
            glassView.bind(viewPager)

            glassView.setCornerRadius(24f.dpToPx().toFloat())

            when (config.materialMode) {
                MaterialMode.GLASS -> {
                    glassView.setRefractionHeight(30f.dpToPx().toFloat())
                    glassView.setRefractionOffset(90f.dpToPx().toFloat())
                    glassView.setBlurRadius(12f.dpToPx().toFloat())
                    glassView.setDispersion(0.5f)
                    glassView.setTintColorRed(1.0f)
                    glassView.setTintColorGreen(1.0f)
                    glassView.setTintColorBlue(1.0f)
                    glassView.setTintAlpha(0.05f)
                }
                MaterialMode.FROSTED -> {
                    glassView.setRefractionHeight(0f)
                    glassView.setRefractionOffset(0f)
                    glassView.setBlurRadius(40f.dpToPx().toFloat())
                    glassView.setDispersion(0f)
                    glassView.setTintColorRed(1.0f)
                    glassView.setTintColorGreen(1.0f)
                    glassView.setTintColorBlue(1.0f)
                    glassView.setTintAlpha(0.25f)
                }
                else -> {
                    glassView.setRefractionHeight(0f)
                    glassView.setBlurRadius(0f)
                    glassView.setTintAlpha(0f)
                }
            }

            glassView.setDraggableEnabled(false)
            glassView.setElasticEnabled(false)
            glassView.setTouchEffectEnabled(false)
            glassView.invalidate()
        } catch (e: Exception) {
            glassView.visibility = View.GONE
        }
    }
}
