package com.example.medlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 所有 ViewModel 的基类，提供统一的异常处理协程扩展。
 *
 * [safeLaunch] 包装 [viewModelScope.launch]，确保：
 * 1. [CancellationException] 正常向上传播（不被吞没）。
 * 2. 其余异常通过 [onError] 回调通知调用方，而不是静默失败。
 *
 * 使用方式（ViewModel 继承 BaseViewModel）：
 * ```
 * fun doSomething() = safeLaunch(
 *     onError = { e -> _uiState.update { it.copy(error = e.message) } }
 * ) {
 *     repository.doWork()
 * }
 * ```
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * 安全协程启动：在 [viewModelScope] 中运行 [block]，
     * 非取消异常通过 [onError] 返回给调用方。
     */
    protected fun safeLaunch(
        onError: (Throwable) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e          // 协程取消不可吞没，必须重新抛出
        } catch (e: Throwable) {
            onError(e)
        }
    }
}
