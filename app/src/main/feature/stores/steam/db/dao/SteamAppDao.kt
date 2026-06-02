package com.winlator.cmod.feature.stores.steam.db.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.feature.stores.steam.service.SteamService.Companion.INVALID_PKG_ID
import kotlin.math.min
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(apps: SteamApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<SteamApp>)

    @Update
    suspend fun update(app: SteamApp)

    @Query(
        "SELECT * FROM steam_app " +
            "WHERE id != 480 " + // Actively filter out Spacewar
            // "AND (owner_account_id IN (:ownerIds) OR license_flags & :borrowedCode = :borrowedCode) " +
            "AND package_id != :invalidPkgId " +
            "AND type != 0 " +
            "ORDER BY LOWER(name)",
    )
    fun getAllOwnedApps(
        // ownerIds: List<Int>,
        invalidPkgId: Int = INVALID_PKG_ID,
        // borrowedCode: Int = ELicenseFlags.Borrowed.code(),
    ): Flow<List<SteamApp>>

    @Query("SELECT * FROM steam_app WHERE received_pics = 0 AND package_id != :invalidPkgId AND owner_account_id = :ownerId")
    fun getAllOwnedAppsWithoutPICS(
        ownerId: Int,
        invalidPkgId: Int = INVALID_PKG_ID,
    ): List<SteamApp>

    @Query("SELECT * FROM steam_app WHERE id = :appId")
    suspend fun findApp(appId: Int): SteamApp?

    /* ----------------------------------------------------------
       INTERNAL bulk queries — keep abstract. Public wrappers
       below chunk inputs so we never exceed SQLite's 999-bind
       ceiling (SQLITE_MAX_VARS).
       ---------------------------------------------------------- */

    @Query("SELECT * FROM steam_app WHERE id IN (:appIds)")
    suspend fun _findApps(appIds: List<Int>): List<SteamApp>

    @Query("SELECT id FROM steam_app WHERE id IN (:appIds)")
    suspend fun _findExistingIds(appIds: List<Int>): List<Int>

    @Query("UPDATE steam_app SET package_id = :packageId WHERE id IN (:appIds)")
    suspend fun _setPackageIdForApps(appIds: List<Int>, packageId: Int)

    @Transaction
    suspend fun findApps(appIds: List<Int>): List<SteamApp> {
        if (appIds.isEmpty()) return emptyList()
        val out = ArrayList<SteamApp>(appIds.size)
        for (i in appIds.indices step SQLITE_MAX_VARS) {
            val end = min(i + SQLITE_MAX_VARS, appIds.size)
            out.addAll(_findApps(appIds.subList(i, end)))
        }
        return out
    }

    @Transaction
    suspend fun findExistingIds(appIds: List<Int>): List<Int> {
        if (appIds.isEmpty()) return emptyList()
        val out = ArrayList<Int>(appIds.size)
        for (i in appIds.indices step SQLITE_MAX_VARS) {
            val end = min(i + SQLITE_MAX_VARS, appIds.size)
            out.addAll(_findExistingIds(appIds.subList(i, end)))
        }
        return out
    }

    @Transaction
    suspend fun setPackageIdForApps(appIds: List<Int>, packageId: Int) {
        if (appIds.isEmpty()) return
        // packageId consumes one bind slot, so chunk size is SQLITE_MAX_VARS - 1.
        val chunk = SQLITE_MAX_VARS - 1
        for (i in appIds.indices step chunk) {
            val end = min(i + chunk, appIds.size)
            _setPackageIdForApps(appIds.subList(i, end), packageId)
        }
    }

    @Query(
        "SELECT * FROM steam_app AS app WHERE dlc_for_app_id = :appId AND depots <> '{}' AND " +
            " EXISTS (" +
            "   SELECT * FROM steam_license AS license " +
            "     WHERE license.license_type <> 0 AND " +
            "       REPLACE(REPLACE(license.app_ids, '[', ','), ']', ',') LIKE ('%,' || app.id || ',%') " +
            ")",
    )
    suspend fun findDownloadableDLCApps(appId: Int): List<SteamApp>

    @Query(
        "SELECT * FROM steam_app AS app WHERE dlc_for_app_id = :appId AND depots = '{}' AND " +
            " EXISTS (" +
            "   SELECT * FROM steam_license AS license " +
            "     WHERE license.license_type <> 0 AND " +
            "       REPLACE(REPLACE(license.app_ids, '[', ','), ']', ',') LIKE ('%,' || app.id || ',%') " +
            ")",
    )
    suspend fun findHiddenDLCApps(appId: Int): List<SteamApp>

    @Query("DELETE from steam_app")
    suspend fun deleteAll()

    @Query("SELECT id FROM steam_app")
    suspend fun getAllAppIds(): List<Int>

    @Query("SELECT * FROM steam_app")
    suspend fun getAllAsList(): List<SteamApp>
}
