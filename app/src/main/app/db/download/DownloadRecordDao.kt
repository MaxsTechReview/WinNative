package com.winlator.cmod.app.db.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("SELECT * FROM download_records ORDER BY created_at ASC")
    suspend fun getAll(): List<DownloadRecord>

    @Query("SELECT * FROM download_records ORDER BY created_at ASC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM download_records WHERE store = :store AND store_game_id = :gameId LIMIT 1")
    suspend fun findByStoreGame(store: String, gameId: String): DownloadRecord?

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadRecord?

    @Query("SELECT * FROM download_records WHERE status = :status ORDER BY created_at ASC")
    suspend fun findByStatus(status: String): List<DownloadRecord>

    @Query("SELECT COUNT(*) FROM download_records WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM download_records WHERE status IN " +
            "('${DownloadRecord.STATUS_COMPLETE}', '${DownloadRecord.STATUS_CANCELLED}', '${DownloadRecord.STATUS_FAILED}')",
    )
    suspend fun deleteFinished(): Int

    @Query("UPDATE download_records SET status = :newStatus, updated_at = :now WHERE status = :oldStatus")
    suspend fun replaceStatus(oldStatus: String, newStatus: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE download_records SET status = :status, updated_at = :now, error_message = :error WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: String,
        error: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        "UPDATE download_records SET bytes_downloaded = :bytesDownloaded, bytes_total = :bytesTotal, updated_at = :now WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Long,
        bytesDownloaded: Long,
        bytesTotal: Long,
        now: Long = System.currentTimeMillis(),
    ): Int
}
