package com.example.medlog.di

import com.example.medlog.ui.ocr.HealthOcrPipeline
import com.example.medlog.ui.ocr.MlKitOcrPipeline
import com.example.medlog.ui.ocr.OcrPipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MlKitOcr

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HealthOcr

@Module
@InstallIn(ViewModelComponent::class)
abstract class OcrModule {

    @Binds
    @MlKitOcr
    abstract fun bindMlKitPipeline(impl: MlKitOcrPipeline): OcrPipeline

    @Binds
    @HealthOcr
    abstract fun bindHealthPipeline(impl: HealthOcrPipeline): OcrPipeline
}
