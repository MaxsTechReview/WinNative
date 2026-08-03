package com.winlator.cmod.feature.stores.gog.db.dao
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.winlator.cmod.feature.stores.gog.data.GOGGame
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import kotlinx.coroutines.flow.Flow

/**
 * DAO for GOG games in the Room database
 */
@Dao
interface GOGGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GOGGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GOGGame>)

    @Update
    suspend fun update(game: GOGGame)

    @Delete
    suspend fun delete(game: GOGGame)

    @Query("DELETE FROM gog_games WHERE id = :gameId AND user_id = :gogAccountId")
    suspend fun deleteById(gameId: String, gogAccountId: String=PrefManager.gogCurrentAccountId)

    @Query("SELECT * FROM gog_games WHERE id = :gameId AND user_id = :gogAccountId")
    suspend fun getById(gameId: String, gogAccountId: String=PrefManager.gogCurrentAccountId): GOGGame?

    @Query("SELECT * FROM gog_games WHERE exclude = 0 AND user_id = :gogAccountId ORDER BY title ASC")
    fun getAll(gogAccountId: String=PrefManager.gogCurrentAccountId): Flow<List<GOGGame>>

    @Query("SELECT * FROM gog_games WHERE exclude = 0 AND user_id = :gogAccountId ORDER BY title ASC")
    suspend fun getAllAsList(gogAccountId: String=PrefManager.gogCurrentAccountId): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE is_installed = :isInstalled AND exclude = 0 AND user_id = :gogAccountId ORDER BY title ASC")
    fun getByInstallStatus(isInstalled: Boolean, gogAccountId: String=PrefManager.gogCurrentAccountId): Flow<List<GOGGame>>

    @Query("SELECT * FROM gog_games WHERE exclude = 0 AND user_id = :gogAccountId AND title LIKE '%' || :searchQuery || '%' ORDER BY title ASC")
    fun searchByTitle(searchQuery: String, gogAccountId: String=PrefManager.gogCurrentAccountId): Flow<List<GOGGame>>

    @Query("DELETE FROM gog_games WHERE is_installed = 0 AND user_id = :gogAccountId")
    suspend fun deleteAllNonInstalledGames(gogAccountId: String=PrefManager.gogCurrentAccountId)

    @Query("SELECT COUNT(*) FROM gog_games WHERE exclude = 0 AND user_id = :gogAccountId")
    fun getCount(gogAccountId: String=PrefManager.gogCurrentAccountId): Flow<Int>

    @Query("SELECT id FROM gog_games WHERE user_id = :gogAccountId")
    suspend fun getAllGameIdsIncludingExcluded(gogAccountId: String=PrefManager.gogCurrentAccountId): List<String>

    /**
     * Upsert GOG games while preserving install status and paths
     * This is useful when refreshing the library from GOG API
     */
    @Transaction
    suspend fun upsertPreservingInstallStatus(games: List<GOGGame>) {
        games.forEach { newGame ->
            val existingGame = getById(newGame.id)
            if (existingGame != null) {
                // Preserve installation status, path, and size from existing game
                val gameToInsert =
                    newGame.copy(
                        isInstalled = existingGame.isInstalled,
                        installPath = existingGame.installPath,
                        installSize = existingGame.installSize,
                        lastPlayed = existingGame.lastPlayed,
                        playTime = existingGame.playTime,
                    )
                insert(gameToInsert)
            } else {
                // New game, insert as-is
                insert(newGame)
            }
        }
    }
}
