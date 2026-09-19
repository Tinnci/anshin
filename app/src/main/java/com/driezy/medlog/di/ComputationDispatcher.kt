package com.driezy.medlog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ComputationModule {
    @Provides
    @ComputationDispatcher
    fun computationDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
