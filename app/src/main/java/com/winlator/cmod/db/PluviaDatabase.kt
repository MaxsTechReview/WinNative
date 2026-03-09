package com.winlator.cmod.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.winlator.cmod.steam.data.ChangeNumbers
import com.winlator.cmod.steam.data.AppInfo
import com.winlator.cmod.steam.data.FileChangeLists
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.SteamLicense
import com.winlator.cmod.steam.data.CachedLicense
import com.winlator.cmod.steam.data.DownloadingAppInfo
import com.winlator.cmod.steam.data.EncryptedAppTicket
import com.winlator.cmod.steam.db.converters.AppConverter
import com.winlator.cmod.steam.db.converters.ByteArrayConverter
import com.winlator.cmod.steam.db.converters.FriendConverter
import com.winlator.cmod.steam.db.converters.LicenseConverter
import com.winlator.cmod.steam.db.converters.PathTypeConverter
import com.winlator.cmod.steam.db.converters.UserFileInfoListConverter
import com.winlator.cmod.steam.db.dao.ChangeNumbersDao
import com.winlator.cmod.steam.db.dao.FileChangeListsDao
import com.winlator.cmod.steam.db.dao.SteamAppDao
import com.winlator.cmod.steam.db.dao.SteamLicenseDao
import com.winlator.cmod.steam.db.dao.AppInfoDao
import com.winlator.cmod.steam.db.dao.CachedLicenseDao
import com.winlator.cmod.steam.db.dao.DownloadingAppInfoDao
import com.winlator.cmod.steam.db.dao.EncryptedAppTicketDao

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
        com.winlator.cmod.epic.data.EpicGame::class,
        DownloadingAppInfo::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(
    AppConverter::class,
    ByteArrayConverter::class,
    FriendConverter::class,
    LicenseConverter::class,
    PathTypeConverter::class,
    UserFileInfoListConverter::class,
    com.winlator.cmod.epic.db.converters.EpicConverter::class,
)
abstract class PluviaDatabase : RoomDatabase() {

    abstract fun epicGameDao(): com.winlator.cmod.epic.db.dao.EpicGameDao

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

        fun init(context: android.content.Context): PluviaDatabase {
            return instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    PluviaDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }

        fun getInstance(context: android.content.Context): PluviaDatabase {
            return init(context)
        }

        fun getInstance(): PluviaDatabase {
            return instance ?: throw IllegalStateException("PluviaDatabase not initialized")
        }
    }
}
