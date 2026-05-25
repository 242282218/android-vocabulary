package com.zzz.androidvocab.core.database

import android.content.Context
import androidx.room.Room
import com.zzz.androidvocab.core.domain.ExportRepository
import com.zzz.androidvocab.core.domain.ReviewRepository
import com.zzz.androidvocab.core.scheduler.FsrsKotlinReviewScheduler
import com.zzz.androidvocab.core.scheduler.ReviewScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {
    @Binds
    @Singleton
    abstract fun bindReviewRepository(repository: OfflineReviewRepository): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(repository: AndroidExportRepository): ExportRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): VocabDatabase =
        Room
            .databaseBuilder(context, VocabDatabase::class.java, "android-vocab.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideWordDao(database: VocabDatabase): WordDao = database.wordDao()

    @Provides
    fun provideReviewDao(database: VocabDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun provideStatsDao(database: VocabDatabase): StatsDao = database.statsDao()

    @Provides
    fun provideExportDao(database: VocabDatabase): ExportDao = database.exportDao()

    @Provides
    @Singleton
    fun provideReviewScheduler(): ReviewScheduler = FsrsKotlinReviewScheduler()
}
