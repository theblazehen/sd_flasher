package dev.blazelight.sdflasher.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.blazelight.sdflasher.data.repository.BlockDeviceRepository
import dev.blazelight.sdflasher.data.repository.ImageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBlockDeviceRepository(): BlockDeviceRepository {
        return BlockDeviceRepository()
    }

    @Provides
    @Singleton
    fun provideImageRepository(
        @ApplicationContext context: Context
    ): ImageRepository {
        return ImageRepository(context)
    }
}
