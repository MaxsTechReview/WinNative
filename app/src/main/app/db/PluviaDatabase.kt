package com.winlator.cmod.app.db
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.winlator.cmod.app.db.download.DownloadRecord
import com.winlator.cmod.app.db.download.DownloadRecordDao
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.stores.gog.service.GOGService

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
        DownloadRecord::class,
    ],
    version = 9,
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

    abstract fun downloadRecordDao(): DownloadRecordDao

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
                    ).addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }

        fun getInstance(context: android.content.Context): PluviaDatabase = init(context)

        fun getInstance(): PluviaDatabase = instance ?: throw IllegalStateException("PluviaDatabase not initialized")

        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE gog_games_new (id TEXT NOT NULL, title TEXT NOT NULL, slug TEXT NOT NULL, download_size INTEGER NOT NULL, install_size INTEGER NOT NULL, is_installed INTEGER NOT NULL, install_path TEXT NOT NULL, image_url TEXT NOT NULL, hero_image_url TEXT NOT NULL, icon_url TEXT NOT NULL, description TEXT NOT NULL, release_date TEXT NOT NULL, developer TEXT NOT NULL, publisher TEXT NOT NULL, genres TEXT NOT NULL, languages TEXT NOT NULL, last_played INTEGER NOT NULL, play_time INTEGER NOT NULL, type INTEGER NOT NULL, exclude INTEGER NOT NULL DEFAULT 0, user_id TEXT NOT NULL, categories TEXT NOT NULL, PRIMARY KEY(id, user_id))")
                    if (PrefManager.gogCurrentAccountId.isEmpty() || !GOGService.isRunning) {
                        db.execSQL("DROP TABLE gog_games")
                        db.execSQL("ALTER TABLE gog_games_new RENAME TO gog_games")
                    } else {
                        db.execSQL("ALTER TABLE gog_games ADD COLUMN user_id TEXT NOT NULL DEFAULT ${PrefManager.gogCurrentAccountId}")
                        db.execSQL("ALTER TABLE gog_games ADD COLUMN categories TEXT NOT NULL DEFAULT ''")

                        db.execSQL("INSERT INTO gog_games_new SELECT * FROM gog_games")
                        db.execSQL("DROP TABLE gog_games")
                        db.execSQL("ALTER TABLE gog_games_new RENAME TO gog_games")
                    }
                }
            }

        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE app_info ADD COLUMN install_path TEXT")
                }
            }

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE gog_games ADD COLUMN hero_image_url TEXT NOT NULL DEFAULT ''")
                }
            }
    }
}
