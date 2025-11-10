package com.exp.hentai1.util

import androidx.lifecycle.Observer

/**
 * 用作通过 LiveData 公开的数据的包装器，该 LiveData 代表一个事件。
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // 允许外部读取，但不允许写入

    /**
     * 返回内容并防止其再次使用。
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * 返回内容，即使它已经被处理过。
     */
    fun peekContent(): T = content
}

/**
 * [Event] 的 [Observer]，简化了检查事件内容是否已被处理的样板代码。
 *
 * 只有在 [Event] 的内容尚未被处理时，才会调用 [onEventUnhandledContent]。
 */
class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        value.getContentIfNotHandled()?.let {
            onEventUnhandledContent(it)
        }
    }
}
