package io.legado.app.ui.file

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import io.legado.app.ui.theme.LegadoThemeWithBackground
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent

class FileManageActivity : AppCompatActivity() {

    private lateinit var viewModel: FileManageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        
        // 与 Compose 层共享同一个 ViewModel 实例
        viewModel = ViewModelProvider(this).get(FileManageViewModel::class.java)
        
        val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH)
        setLegadoContent {
            FileManageScreen(
                viewModel = viewModel,
                initialPath = initialPath,
                onBackClick = { finish() }
            )
        }
    }

    /**
     * 拦截物理返回键和返回手势
     * 优先级：子目录 → 上级目录 → 存储根目录 → 选择面板 → 退出 Activity
     */
    override fun onBackPressed() {
        when {
            viewModel.subDocsFlow.value.isNotEmpty() -> viewModel.gotoLastDir()
            viewModel.currentStorage.value != FileManageViewModel.StorageType.NONE -> viewModel.goToRoot()
            else -> super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_INITIAL_PATH = "initialPath"
    }
}

@Composable
fun FileManageContent(
    initialPath: String? = null,
    onBackClick: () -> Unit
) {
    LegadoThemeWithBackground(backgroundDrawable = null) {
        FileManageScreen(
            initialPath = initialPath,
            onBackClick = onBackClick
        )
    }
}