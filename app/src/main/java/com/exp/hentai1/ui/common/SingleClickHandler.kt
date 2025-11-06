package com.exp.hentai1.ui.common

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

class SingleClickHandler(
    private val onClick: () -> Unit
) {
    private val debounceInterval = 200L
    private var lastClickTime = 0L

    fun handleClick() {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastClickTime > debounceInterval) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

fun Modifier.singleClickable(onClick: () -> Unit): Modifier = composed {
    val singleClickHandler = SingleClickHandler(onClick)
    clickable { singleClickHandler.handleClick() }
}
