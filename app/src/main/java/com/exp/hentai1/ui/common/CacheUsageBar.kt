package com.exp.hentai1.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun CacheUsageBar(
    currentSizeMB: Float,
    maxSizeMB: Float,
    modifier: Modifier = Modifier
) {
    val usageRatio = if (maxSizeMB > 0) (currentSizeMB / maxSizeMB).coerceAtMost(1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        // Used space bar
        Box(
            modifier = Modifier
                .fillMaxWidth(usageRatio)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.primary)
        )

        // Text in the center
        Text(
            text = String.format(Locale.US, "%.1f MB / %.1f MB", currentSizeMB, maxSizeMB),
            modifier = Modifier.align(Alignment.Center),
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
