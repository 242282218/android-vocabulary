package com.zzz.androidvocab

import com.zzz.androidvocab.core.common.ClockProvider
import com.zzz.androidvocab.core.common.SystemClockProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindClockProvider(provider: SystemClockProvider): ClockProvider
}
