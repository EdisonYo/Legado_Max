package io.legado.app.ui.file

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.utils.openFileUri
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 文件管理 ViewModel
 */
class FileManageViewModel(application: Application) : BaseViewModel(application) {

    enum class StorageType { NONE, EXTERNAL, INTERNAL }

    /** 外部存储根目录：/sdcard/Android/data/<package>/ */
    val externalRoot = context.getExternalFilesDir(null)?.parentFile

    /** 内部存储根目录：/data/data/<package>/ */
    val internalRoot = context.filesDir.parentFile

    /** 当前选中的存储类型 */
    private val _currentStorage = MutableStateFlow(StorageType.NONE)
    val currentStorage: StateFlow<StorageType> = _currentStorage.asStateFlow()

    /** 当前存储的根目录 */
    private val currentRoot: File? get() = when (_currentStorage.value) {
        StorageType.EXTERNAL -> externalRoot
        StorageType.INTERNAL -> internalRoot
        else -> null
    }

    /** 子目录列表（用于路径导航条显示） */
    private val _subDocs = MutableStateFlow<MutableList<File>>(mutableListOf())
    val subDocsFlow: StateFlow<List<File>> = _subDocs.asStateFlow()

    /** 当前目录下的文件列表 */
    private val _filesLiveData = MutableStateFlow<List<File>>(emptyList())
    val filesLiveData: StateFlow<List<File>> = _filesLiveData.asStateFlow()

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 当前未过滤的文件列表 */
    private var currentFiles = listOf<File>()

    /** 当前目录的上级目录（用于显示 ".." 项） */
    val lastDir: File? get() = _subDocs.value.lastOrNull() ?: currentRoot

    init {
        // 初始不加载任何目录，等待用户选择存储
        _filesLiveData.value = emptyList()
    }

    fun selectExternalStorage() {
        _currentStorage.value = StorageType.EXTERNAL
        _subDocs.value = mutableListOf()
        upFiles(externalRoot)
    }

    fun selectInternalStorage() {
        _currentStorage.value = StorageType.INTERNAL
        _subDocs.value = mutableListOf()
        upFiles(internalRoot)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterFiles()
    }

    private fun filterFiles() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            currentFiles.filter {
                it.name == ".." || it.name.contains(query)
            }.let {
                _filesLiveData.value = it
            }
        } else {
            _filesLiveData.value = currentFiles
        }
    }

	fun upFiles(parentFile: File?) {
	    viewModelScope.launch {
	        try {
	            parentFile ?: return@launch
	            val root = currentRoot
	            val result = if (parentFile == root) {
	                parentFile.listFiles()?.sortedWith(
	                    compareBy({ it.isFile }, { it.name })
	                ) ?: emptyList()
	            } else {
	                val list = arrayListOf(parentFile)
	                if (parentFile.exists()) {
	                    parentFile.listFiles()?.sortedWith(
	                        compareBy({ it.isFile }, { it.name })
	                    )?.let { list.addAll(it) }
	                }
	                list
	            }
	            currentFiles = result
	            _searchQuery.value = ""
	            _filesLiveData.value = result
	        } catch (e: Exception) {
	            context.toastOnUi(e.localizedMessage)
	        }
	    }
	}

    /**
     * 点击 root 时的行为：
     * - 如果在子目录，回到该存储根目录
     * - 如果已在存储根目录，回到选择界面
     */
    fun goToRoot() {
        if (_subDocs.value.isEmpty()) {
            _currentStorage.value = StorageType.NONE
            _filesLiveData.value = emptyList()
        } else {
            _subDocs.value = mutableListOf()
            upFiles(currentRoot)
        }
    }

    fun goToPath(index: Int) {
        val newSubDocs = _subDocs.value.subList(0, index + 1).toMutableList()
        _subDocs.value = newSubDocs
        upFiles(newSubDocs.lastOrNull())
    }

    fun gotoLastDir() {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.removeLastOrNull()
        _subDocs.value = currentSubDocs
        upFiles(lastDir)
    }

    fun enterDir(file: File) {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.add(file)
        _subDocs.value = currentSubDocs
        upFiles(file)
    }

    fun openPath(path: String) {
        val target = File(path).let { file ->
            when {
                file.isDirectory -> file
                file.isFile -> file.parentFile
                else -> file
            }
        } ?: return

        when {
            externalRoot != null && target.absolutePath.startsWith(externalRoot.absolutePath) -> {
                _currentStorage.value = StorageType.EXTERNAL
            }
            internalRoot != null && target.absolutePath.startsWith(internalRoot.absolutePath) -> {
                _currentStorage.value = StorageType.INTERNAL
            }
            else -> {
                _currentStorage.value = StorageType.EXTERNAL
            }
        }

        _subDocs.value = buildPathChain(target).toMutableList()
        upFiles(target)
    }

    private fun buildPathChain(target: File): List<File> {
        val root = currentRoot
        if (root != null && target.absolutePath.startsWith(root.absolutePath)) {
            val chain = mutableListOf<File>()
            var current: File? = target
            while (current != null && current != root) {
                chain.add(current)
                current = current.parentFile
            }
            return chain.asReversed()
        }
        return generateSequence(target) { it.parentFile }
            .toList()
            .asReversed()
            .filter { it.parentFile != null }
    }

    fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                AppConst.authority,
                file
            )
            context.openFileUri(uri)
        } catch (e: Exception) {
            context.toastOnUi(e.localizedMessage)
        }
    }

    fun delFile(file: File) {
        viewModelScope.launch {
            try {
                val success = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                context.toastOnUi(
                    if (success) "删除成功" else "删除失败"
                )
                upFiles(lastDir)
            } catch (e: Exception) {
                context.toastOnUi(e.localizedMessage)
            }
        }
    }
}