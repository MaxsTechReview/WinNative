package com.winlator.cmod.app.di
import android.content.Context
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.feature.stores.steam.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PluviaDatabase = PluviaDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideSteamLicenseDao(db: PluviaDatabase) = db.steamLicenseDao()

    @Provides
    @Singleton
    fun provideSteamAppDao(db: PluviaDatabase) = db.steamAppDao()

    @Provides
    @Singleton
    fun provideAppChangeNumbersDao(db: PluviaDatabase) = db.appChangeNumbersDao()

    @Provides
    @Singleton
    fun provideAppFileChangeListsDao(db: PluviaDatabase) = db.appFileChangeListsDao()

    @Provides
    @Singleton
    fun provideAppInfoDao(db: PluviaDatabase): AppInfoDao = db.appInfoDao()

    @Provides
    @Singleton
    fun provideCachedLicenseDao(db: PluviaDatabase): CachedLicenseDao = db.cachedLicenseDao()

    @Provides
    @Singleton
    fun provideEncryptedAppTicketDao(db: PluviaDatabase): EncryptedAppTicketDao = db.encryptedAppTicketDao()

    @Provides
    @Singleton
    fun provideDownloadingAppInfoDao(db: PluviaDatabase): DownloadingAppInfoDao = db.downloadingAppInfoDao()

    @Provides
    @Singleton
    fun provideEpicGameDao(db: PluviaDatabase): com.winlator.cmod.feature.stores.epic.db.dao.EpicGameDao = db.epicGameDao()

    @Provides
    @Singleton
    fun provideGogGameDao(db: PluviaDatabase): com.winlator.cmod.feature.stores.gog.db.dao.GOGGameDao = db.gogGameDao()

    @Provides
    @Singleton
    fun provideDownloadRecordDao(db: PluviaDatabase): com.winlator.cmod.app.db.download.DownloadRecordDao = db.downloadRecordDao()
}
