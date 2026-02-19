package com.example.medlog.di

import android.content.Context
import androidx.room.Room
import com.example.medlog.data.local.MedLogDatabase
import com.example.medlog.data.local.MedicationDao
import com.example.medlog.data.local.MedicationLogDao
import com.example.medlog.data.repository.DrugRepository
import com.example.medlog.data.repository.DrugRepositoryImpl
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.MedicationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MedLogDatabase =
        Room.databaseBuilder(
            context,
            MedLogDatabase::class.java,
            "medlog.db",
        )
            .addMigrations(MedLogDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideMedicationDao(db: MedLogDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideMedicationLogDao(db: MedLogDatabase): MedicationLogDao = db.medicationLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(
        impl: MedicationRepositoryImpl,
    ): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindDrugRepository(
        impl: DrugRepositoryImpl,
    ): DrugRepository
}

