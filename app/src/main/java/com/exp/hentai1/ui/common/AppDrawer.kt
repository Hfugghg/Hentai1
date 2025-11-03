package com.exp.hentai1.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
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
        MenuItem("标签", Icons.AutoMirrored.Filled.List),
        MenuItem("原作", Icons.Default.Create),
        MenuItem("角色", Icons.Default.Person),
        MenuItem("作者", Icons.Default.Person),
        MenuItem("社团", Icons.Default.Person),
        MenuItem("排行榜", Icons.Default.Star),
        MenuItem("我的收藏", Icons.Default.Favorite),
        MenuItem("设置", Icons.Default.Settings)
    )

    Column(modifier = Modifier.padding(8.dp)) {
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
