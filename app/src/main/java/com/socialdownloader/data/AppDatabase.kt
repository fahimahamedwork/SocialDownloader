package com.socialdownloader.data

import androidx.room.*
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.socialdownloader.data.model.DownloadItem
import com.socialdownloader.data.model.DownloadStatus
import com.socialdownloader.data.model.Platform
import kotlinx.coroutines.flow.Flow

// ─── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromPlatform(platform: Platform): String = platform.name

    @TypeConverter
    fun toPlatform(name: String): Platform = Platform.valueOf(name)

    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(name: String): DownloadStatus = DownloadStatus.valueOf(name)
}

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE platform = :platform ORDER BY createdAt DESC")
    fun getDownloadsByPlatform(platform: Platform): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Query("SELECT * FROM downloads WHERE workerId = :workerId LIMIT 1")
    suspend fun getDownloadByWorkerId(workerId: String): DownloadItem?

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'PENDING', 'FETCHING_INFO') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    fun getActiveDownloadCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadedBytes(): Long?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem): Long

    @Update
    suspend fun updateDownload(item: DownloadItem)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String = "")

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :time, filePath = :path WHERE id = :id")
    suspend fun markCompleted(id: Long, time: Long, path: String)

    @Delete
    suspend fun deleteDownload(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("SELECT * FROM downloads WHERE title LIKE '%' || :query || '%' OR platform LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchDownloads(query: String): Flow<List<DownloadItem>>
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [DownloadItem::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
