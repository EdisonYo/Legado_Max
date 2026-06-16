package io.legado.app.ui.main.navigationbar.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.data.entities.TabIconConfig
import io.legado.app.model.TabIconPreset

/**
 * Tab 图标选择器弹窗
 *
 * @param tabKey 当前选择的 tab 键名（bookshelf/discovery/rss/my）
 * @param currentConfig 当前图标配置
 * @param onConfirm 确认回调，返回新的 TabIconConfig
 * @param onDismiss 取消回调
 * @param onPickCustomFile 选择自定义文件回调
 */
@Composable
fun TabIconPickerDialog(
    tabKey: String,
    currentConfig: TabIconConfig,
    onConfirm: (TabIconConfig) -> Unit,
    onDismiss: () -> Unit,
    onPickCustomFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var useCustom by remember(currentConfig.isCustom) {
        mutableStateOf(currentConfig.isCustom)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题
                Text(
                    text = "选择${TabIconPreset.TAB_DISPLAY_NAMES[tabKey] ?: ""}图标",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 默认图标选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { useCustom = false }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !useCustom,
                        onClick = { useCustom = false }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 默认图标预览
                    val drawableResId = TabIconPreset.getPreviewDrawableResId(tabKey, "default")
                    Image(
                        painter = painterResource(drawableResId),
                        contentDescription = "默认",
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(
                            if (!useCustom) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "默认图标",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!useCustom) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 自定义图标选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            useCustom = true
                            onPickCustomFile()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = useCustom,
                        onClick = {
                            useCustom = true
                            onPickCustomFile()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 自定义图标预览（如果已选择）
                    if (useCustom && currentConfig.isCustom && currentConfig.customIconPath != null) {
                        val customBitmap = remember(currentConfig.customIconPath) {
                            try {
                                val file = java.io.File(currentConfig.customIconPath)
                                if (file.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(currentConfig.customIconPath)
                                } else null
                            } catch (e: Exception) { null }
                        }
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = "自定义图标",
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    Text(
                        text = if (useCustom && currentConfig.isCustom) "已选择自定义图标" else "从文件选择图标",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (useCustom) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = {
                        val newConfig = if (useCustom) {
                            currentConfig.copy(presetName = "default")
                        } else {
                            TabIconConfig(presetName = "default")
                        }
                        onConfirm(newConfig)
                    }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
