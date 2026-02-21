package com.example.medlog.di

import android.content.Context
import androidx.room.Room
import com.example.medlog.data.local.HealthRecordDao
import com.example.medlog.data.local.MedLogDatabase
import com.example.medlog.data.local.MedicationDao
import com.example.medlog.data.local.MedicationLogDao
import com.example.medlog.data.local.SymptomLogDao
import com.example.medlog.data.repository.DrugRepository
import com.example.medlog.data.repository.DrugRepositoryImpl
import com.example.medlog.data.repository.HealthRepository
import com.example.medlog.data.repository.HealthRepositoryImpl
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.LogRepositoryImpl
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.MedicationRepositoryImpl
import com.example.medlog.data.repository.SymptomRepository
import com.example.medlog.data.repository.SymptomRepositoryImpl
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
                    .addMigrations(
                MedLogDatabase.MIGRATION_5_6,
                MedLogDatabase.MIGRATION_6_7,
                MedLogDatabase.MIGRATION_7_8,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMedicationDao(db: MedLogDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideMedicationLogDao(db: MedLogDatabase): MedicationLogDao = db.medicationLogDao()

    @Provides
    fun provideSymptomLogDao(db: MedLogDatabase): SymptomLogDao = db.symptomLogDao()

    @Provides
    fun provideHealthRecordDao(db: MedLogDatabase): HealthRecordDao = db.healthRecordDao()
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
    abstract fun bindLogRepository(
        impl: LogRepositoryImpl,
    ): LogRepository

    @Binds
    @Singleton
    abstract fun bindDrugRepository(
        impl: DrugRepositoryImpl,
    ): DrugRepository

    @Binds
    @Singleton
    abstract fun bindSymptomRepository(
        impl: SymptomRepositoryImpl,
    ): SymptomRepository

    @Binds
    @Singleton
    abstract fun bindHealthRepository(
        impl: HealthRepositoryImpl,
    ): HealthRepository
}

