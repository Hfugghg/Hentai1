package com.exp.hentai1.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: AboutViewModel = viewModel() // 自动注入 ViewModel
) {
    // 收集 ViewModel 中的状态
    val libs by viewModel.libs.collectAsState()
    val versionName by viewModel.versionName.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("关于") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->

        // AboutPage 必须等到 libs 加载完毕后才能显示
        val loadedLibs = libs
        if (loadedLibs != null) {
            // 使用 AboutLibraries 提供的专用 Composable
            LibrariesContainer(
                libraries = loadedLibs,
                modifier = Modifier.padding(paddingValues),
                // 自定义列表项的颜色（可选）
                colors = LibraryDefaults.libraryColors(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    badgeBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                // ▼ 关键：自定义页眉 ▼
                header = {
                    item {
                        AppHeader(versionName = versionName) // 传入版本号
                    }
                }
            )
        } else {
            // 在数据加载时显示一个加载圈
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(100.dp) // 避免被 TopAppBar 遮挡
            )
        }
    }
}

/**
 * 自定义的“关于”页面顶部
 */
@Composable
private fun AppHeader(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ▼▼▼ 修复：使用来自 core 库的图标 ▼▼▼
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks, //
            contentDescription = "App Icon",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        // ▲▲▲

        Spacer(modifier = Modifier.height(16.dp))

        // App 名称 (来自你的包名)
        Text(
            text = "Hentai1",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 显示版本号
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "感谢所有开源贡献者",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
