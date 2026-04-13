package com.winlator.cmod.app.db
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.winlator.cmod.feature.stores.steam.data.AppInfo
import com.winlator.cmod.feature.stores.steam.data.CachedLicense
import com.winlator.cmod.feature.stores.steam.data.ChangeNumbers
import com.winlator.cmod.feature.stores.steam.data.DownloadingAppInfo
import com.winlator.cmod.feature.stores.steam.data.EncryptedAppTicket
import com.winlator.cmod.feature.stores.steam.data.FileChangeLists
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.feature.stores.steam.data.SteamLicense
import com.winlator.cmod.feature.stores.steam.db.converters.AppConverter
import com.winlator.cmod.feature.stores.steam.db.converters.ByteArrayConverter
import com.winlator.cmod.feature.stores.steam.db.converters.FriendConverter
import com.winlator.cmod.feature.stores.steam.db.converters.LicenseConverter
import com.winlator.cmod.feature.stores.steam.db.converters.PathTypeConverter
import com.winlator.cmod.feature.stores.steam.db.converters.UserFileInfoListConverter
import com.winlator.cmod.feature.stores.steam.db.dao.AppInfoDao
import com.winlator.cmod.feature.stores.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.feature.stores.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.feature.stores.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.feature.stores.steam.db.dao.EncryptedAppTicketDao
import com.winlator.cmod.feature.stores.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.feature.stores.steam.db.dao.SteamAppDao
import com.winlator.cmod.feature.stores.steam.db.dao.SteamLicenseDao

const val DATABASE_NAME = "pluvia_database"

@Database(
    entities = [
        AppInfo::class,
        CachedLicense::class,
        ChangeNumbers::class,
        EncryptedAppTicket::class,
        FileChangeLists::class,
        SteamApp::class,
        SteamLicense::class,
        com.winlator.cmod.feature.stores.epic.data.EpicGame::class,
        com.winlator.cmod.feature.stores.gog.data.GOGGame::class,
        DownloadingAppInfo::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(
    AppConverter::class,
    ByteArrayConverter::class,
    FriendConverter::class,
    LicenseConverter::class,
    PathTypeConverter::class,
    UserFileInfoListConverter::class,
    com.winlator.cmod.feature.stores.epic.db.converters.EpicConverter::class,
)
abstract class PluviaDatabase : RoomDatabase() {
    abstract fun epicGameDao(): com.winlator.cmod.feature.stores.epic.db.dao.EpicGameDao

    abstract fun gogGameDao(): com.winlator.cmod.feature.stores.gog.db.dao.GOGGameDao

    abstract fun steamLicenseDao(): SteamLicenseDao

    abstract fun steamAppDao(): SteamAppDao

    abstract fun appChangeNumbersDao(): ChangeNumbersDao

    abstract fun appFileChangeListsDao(): FileChangeListsDao

    abstract fun appInfoDao(): AppInfoDao

    abstract fun cachedLicenseDao(): CachedLicenseDao

    abstract fun encryptedAppTicketDao(): EncryptedAppTicketDao

    abstract fun downloadingAppInfoDao(): DownloadingAppInfoDao

    companion object {
        @Volatile
        private var instance: PluviaDatabase? = null

        fun init(context: android.content.Context): PluviaDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room
                    .databaseBuilder(
                        context.applicationContext,
                        PluviaDatabase::class.java,
                        DATABASE_NAME,
                    ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        fun getInstance(context: android.content.Context): PluviaDatabase = init(context)

        fun getInstance(): PluviaDatabase = instance ?: throw IllegalStateException("PluviaDatabase not initialized")
    }
}
