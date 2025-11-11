package com.exp.hentai1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exp.hentai1.data.cache.AppCache
import com.exp.hentai1.data.remote.NetworkUtils
import com.exp.hentai1.ui.about.AboutScreen
import com.exp.hentai1.ui.detail.DetailScreen
import com.exp.hentai1.ui.favorites.FavoritesScreen
import com.exp.hentai1.ui.home.HomeScreen
import com.exp.hentai1.ui.home.HomeViewModel
import com.exp.hentai1.ui.local.LocalScreen
import com.exp.hentai1.ui.parseTags.ParseTagsScreen
import com.exp.hentai1.ui.ranking.RankingMoreScreen
import com.exp.hentai1.ui.reader.ReaderScreen
import com.exp.hentai1.ui.search.SearchResultScreen
import com.exp.hentai1.ui.settings.SettingsScreen
import com.exp.hentai1.ui.theme.Hentai1Theme
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(application) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppCache.initialize(this)
        setContent {
            Hentai1Theme {
                Hentai1App(viewModel)
                // 检查更新
                UpdateChecker(viewModel)
            }
        }
    }
}

@Composable
fun Hentai1App(viewModel: HomeViewModel) {
    val navController = rememberNavController()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onComicClick = { comicId ->
                        navController.navigate("detail/$comicId")
                    },
                    onFavoritesClick = { navController.navigate("favorites") },
                    onRankingMoreClick = { navController.navigate("rankingMore") },
                    onSearch = { query -> navController.navigate("search/$query") },
                    onMenuClick = { route -> navController.navigate(route) }
                )
            }
            composable(
                route = "detail/{comicId}",
                arguments = listOf(
                    navArgument("comicId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val comicId = backStackEntry.arguments?.getString("comicId")
                if (comicId != null) {
                    DetailScreen(
                        comicId = comicId,
                        onNavigateToReader = { id ->
                            navController.navigate("reader/$id")
                        },
                        onNavigateToTagSearch = { query ->
                            navController.navigate("search/$query")
                        }
                    )
                }
            }
            composable(
                route = "reader/{comicId}",
                arguments = listOf(
                    navArgument("comicId") { type = NavType.StringType }
                )
            ) {
                val comicId = it.arguments?.getString("comicId")
                if (comicId != null) {
                    ReaderScreen(comicId = comicId)
                }
            }
            composable("favorites") {
                FavoritesScreen(onComicClick = { comicId ->
                    navController.navigate("detail/$comicId")
                })
            }
            composable("rankingMore") {
                RankingMoreScreen(onComicClick = { comicId ->
                    navController.navigate("detail/$comicId")
                })
            }
            composable(
                route = "search/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType })
            ) {
                val query = it.arguments?.getString("query")
                if (query != null) {
                    SearchResultScreen(
                        query = query,
                        onComicClick = { comicId ->
                            navController.navigate("detail/$comicId")
                        }
                    )
                }
            }
            composable(
                "list/{entityType}",
                arguments = listOf(navArgument("entityType") { type = NavType.StringType })
            ) {
                val entityType = it.arguments?.getString("entityType")
                if (entityType != null) {
                    ParseTagsScreen(
                        onTagClick = { query ->
                            navController.navigate("search/$query")
                        },
                        entityType = entityType
                    )
                }
            }
            composable("settings") {
                SettingsScreen()
            }
            composable("local") {
                LocalScreen(
                    onNavigateToDetail = { comicId ->
                        navController.navigate("detail/$comicId")
                    }
                )
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}


data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val assets: List<Asset>
)

data class Asset(
    @SerializedName("browser_download_url") val downloadUrl: String
)

@Composable
fun UpdateChecker(viewModel: HomeViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var releaseInfo by remember { mutableStateOf<GitHubRelease?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch {
            val release = checkForUpdates()
            if (release != null) {
                val currentVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                currentVersionName?.let {
                    if (release.tagName.removePrefix("v") > it) {
                        releaseInfo = release
                        showDialog = true
                    }
                }
            }
        }
    }

    if (showDialog && releaseInfo != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("发现新版本") },
            text = { Text("新版本 ${releaseInfo!!.tagName} 已发布，要现在下载吗？") },
            confirmButton = {
                TextButton(onClick = { /* TODO: 实现下载 */ }) {
                    Text("下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("稍后")
                }
            }
        )
    }
}

suspend fun checkForUpdates(): GitHubRelease? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(NetworkUtils.GITHUB_RELEASE_URL).build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string()
            if (body != null) {
                val release = Gson().fromJson(body, GitHubRelease::class.java)
                if (release.assets.any { it.downloadUrl.endsWith(".apk") }) {
                    return@withContext release
                }
            }
        }
    } catch (_: Exception) {
        // 处理错误，可以记录日志
    }
    return@withContext null
}
