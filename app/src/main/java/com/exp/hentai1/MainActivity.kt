package com.exp.hentai1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exp.hentai1.ui.detail.DetailScreen
import com.exp.hentai1.ui.favorites.FavoritesScreen
import com.exp.hentai1.ui.home.HomeScreen
import com.exp.hentai1.ui.home.HomeViewModel
import com.exp.hentai1.ui.ranking.RankingMoreScreen
import com.exp.hentai1.ui.reader.ReaderScreen
import com.exp.hentai1.ui.theme.Hentai1Theme

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
        setContent {
            Hentai1Theme {
                Hentai1App(viewModel)
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
                    onRankingMoreClick = { navController.navigate("rankingMore") } // 新增的导航
                )
            }
            composable(
                route = "detail/{comicId}",
                arguments = listOf(navArgument("comicId") { type = NavType.StringType })
            ) {
                val comicId = it.arguments?.getString("comicId")
                if (comicId != null) {
                    DetailScreen(
                        comicId = comicId,
                        onNavigateToReader = { id ->
                            navController.navigate("reader/$id")
                        }
                    )
                }
            }
            composable(
                route = "reader/{comicId}",
                arguments = listOf(navArgument("comicId") { type = NavType.StringType })
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
            composable("rankingMore") { // 新增的更多排行路由
                RankingMoreScreen()
            }
        }
    }
}
