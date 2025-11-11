package com.exp.hentai1.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class MenuItem(val title: String, val icon: ImageVector)

@Composable
fun AppDrawer(
    onMenuClick: (String) -> Unit
) {
    val menuItems = listOf(
        // "标签": LocalOffer (核心库)
        MenuItem("标签", Icons.Default.LocalOffer),

        // "原作": AutoStories (扩展库)
        // 修正：直接使用 Icons.Default.AutoStories
        MenuItem("原作", Icons.Default.AutoStories),

        // "角色": RecentActors (扩展库)
        MenuItem("角色", Icons.Default.RecentActors),

        // "作者": Brush (扩展库)
        MenuItem("作者", Icons.Default.Brush),

        // "团队": Group (核心库)
        MenuItem("团队", Icons.Default.Group),

        // "排行": Leaderboard (扩展库)
        // 修正：直接使用 Icons.Default.Leaderboard
        MenuItem("排行", Icons.Default.Leaderboard),

        // "本地": Folder (核心库)
        MenuItem("本地", Icons.Default.Folder),

        // "收藏": Favorite (核心库)
        MenuItem("收藏", Icons.Default.Favorite),

        // "设置": Settings (核心库)
        MenuItem("设置", Icons.Default.Settings),

        // "关于": Info (核心库)
        MenuItem("关于", Icons.Default.Info)
    )

    Column(
        modifier = Modifier
            .padding(8.dp)
            .widthIn(max = 280.dp) // 设置最大宽度，防止在宽屏设备上过宽
            .fillMaxWidth(0.7f) // 在小屏设备上占据屏幕宽度的 70%
    ) {
        menuItems.forEach { item ->
            DrawerItem(item = item, onClick = { onMenuClick(item.title) })
        }
    }
}

@Composable
fun DrawerItem(item: MenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = item.icon, contentDescription = item.title)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
    }
}
