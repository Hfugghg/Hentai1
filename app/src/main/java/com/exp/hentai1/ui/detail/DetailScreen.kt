package com.exp.hentai1.ui.detail

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.NetworkUtils

// 为 DetailViewModel 创建一个 Factory
class DetailViewModelFactory(private val application: Application, private val comicId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(application, comicId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun DetailScreen(
    comicId: String,
    onNavigateToReader: (String) -> Unit
) {
    // 使用 Factory 来创建 ViewModel 实例
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModelFactory(LocalContext.current.applicationContext as Application, comicId)
    )
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "正在加载漫画详情...")
            }
        }
        uiState.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "发生错误: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        uiState.comic != null -> {
            ComicDetailContent(comic = uiState.comic!!, onReadClick = {
                onNavigateToReader(comicId)
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailContent(comic: Comic, onReadClick: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = comic.coverUrl,
                    contentDescription = comic.title,
                    modifier = Modifier.height(400.dp) // 增加高度以获得更好的视觉效果
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 详细信息区域
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailInfoRow("作者", comic.author)
                DetailInfoRow("社团", comic.circle)
                DetailInfoRow("原作", comic.parody)
                DetailInfoRow("分类", comic.category)
                DetailInfoRow("角色", comic.character)
                DetailInfoRow("语言", comic.language)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 功能按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(onClick = onReadClick) {
                    Text("阅读")
                }
                Button(onClick = { /*TODO*/ }) {
                    Text("加入收藏")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 标签区域
            if (comic.tags.isNotEmpty()) {
                Text(text = "标签:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    comic.tags.forEach { tag ->
                        Button(onClick = {
                            val url = NetworkUtils.tagUrl(tag.id)
                            val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
//                            context.startActivity(intent)
                        }) {
                            Text(text = tag.name)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 图片列表
        if (comic.imageList.isNotEmpty()) {
            item {
                Text(text = "图片列表:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(comic.imageList) { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            // 主人说其他数据也是按钮，但目前还没有ID，所以暂时显示为文本
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
