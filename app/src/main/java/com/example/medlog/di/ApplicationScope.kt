package com.example.medlog.di

import javax.inject.Qualifier

/**
 * Hilt 限定符：标记应用级别 [kotlinx.coroutines.CoroutineScope]。
 *
 * 生命周期 = Application，超时 = 永不自动取消。
 * 仅在 @Singleton 组件中使用。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
