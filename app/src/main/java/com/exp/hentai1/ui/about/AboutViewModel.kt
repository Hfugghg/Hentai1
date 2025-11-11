// AboutViewModel.kt
package com.exp.hentai1.ui.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutViewModel(private val app: Application) : AndroidViewModel(app) {

    // 用于存储从 AboutLibraries 库加载的库列表
    private val _libs = MutableStateFlow<Libs?>(null)
    val libs: StateFlow<Libs?> = _libs.asStateFlow()

    // 用于存储 App 版本号
    private val _versionName = MutableStateFlow("")
    val versionName: StateFlow<String> = _versionName.asStateFlow()

    init {
        // ViewModel 初始化时，立即开始加载
        loadAppVersion()
        loadLicenseData()
    }

    /**
     * 从包管理器获取当前 App 的版本号
     */
    private fun loadAppVersion() {
        try {
            val packageName = app.packageName
            val packageInfo = app.packageManager.getPackageInfo(packageName, 0)
            _versionName.value = packageInfo.versionName.toString() // "1.0"
        } catch (e: Exception) {
            e.printStackTrace()
            _versionName.value = "N/A" // 出错时的回退
        }
    }

    /**
     * 异步加载所有开源库信息
     * 这是一个 I/O 密集型操作，所以使用 Dispatchers.IO
     */
    private fun loadLicenseData() {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryData = Libs.Builder()
                .withContext(app) // 传入 Context
                .build() // 构建数据

            _libs.value = libraryData
        }
    }
}