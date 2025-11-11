package com.exp.hentai1.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exp.hentai1.data.Comic
import com.exp.hentai1.data.remote.HentaiOneSite
import com.exp.hentai1.ui.common.ComicCard
import com.exp.hentai1.ui.common.ComicCardStyle

/**
 * 集中放置主页使用的较小、可复用组件。
 * 使用 object 包装，便于在 HomeScreen 中导入所有组件。
 */
object HomeComponents {

    @Composable
    fun CenteredIndicatorWithText(text: String, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text)
        }
    }

    @Composable
    fun SiteSwitchBar(
        currentSite: HentaiOneSite,
        onSiteSelected: (HentaiOneSite) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // 将站点与其显示名称关联起来，便于维护
        val siteDisplayNames = remember {
            mapOf(
                HentaiOneSite.MAIN to "日本語",
                HentaiOneSite.CHINESE to "中文",
                HentaiOneSite.ENGLISH to "English"
            )
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用 HentaiOneSite.entries 动态创建按钮，更具扩展性
            HentaiOneSite.entries.forEach { site ->
                val isSelected = currentSite == site
                val siteName = siteDisplayNames[site] ?: site.name

                TextButton(
                    onClick = { onSiteSelected(site) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = siteName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }

    @Composable
    fun SectionTitle(
        title: String,
        modifier: Modifier = Modifier,
        onMoreClick: (() -> Unit)? = null,
        extraText: String? = null,
        onExtraTextClick: (() -> Unit)? = null
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (onMoreClick != null) {
                Text(
                    text = "更多",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onMoreClick)
                )
            } else if (extraText != null) {
                Text(
                    text = extraText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = onExtraTextClick != null, onClick = { onExtraTextClick?.invoke() })
                )
            }
        }
    }

    @Composable
    fun RankingSection(comics: List<Comic>, onComicClick: (String) -> Unit, rankingListState: LazyListState) {
        if (comics.isEmpty()) {
            Text(
                text = "没有找到排行数据",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            // 使用 BoxWithConstraints 来获取父容器的宽度信息
            BoxWithConstraints {
                val screenWidth = maxWidth
                // 定义响应式断点
                val cardWidth = if (screenWidth < 600.dp) {
                    100.dp // 手机竖屏等窄屏幕
                } else {
                    140.dp // 平板或手机横屏等宽屏幕
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    state = rankingListState
                ) {
                    items(comics, key = { it.id }) { comic ->
                        ComicCard(
                            comic = comic,
                            style = ComicCardStyle.GRID,
                            modifier = Modifier.width(cardWidth), // 应用动态计算的宽度
                            onComicClick = onComicClick
                        )
                    }
                }
            }
        }
    }
}