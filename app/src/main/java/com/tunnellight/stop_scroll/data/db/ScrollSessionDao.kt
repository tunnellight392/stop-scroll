package com.tunnellight.stop_scroll.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollSessionDao {

    @Upsert
    suspend fun upsert(session: ScrollSessionEntity)

    /** Every session that overlaps [from, to). Overlap, not containment, so a session that spans a boundary is included and can be clipped. */
    @Query("SELECT * FROM scroll_sessions WHERE endTime > :from AND startTime < :to ORDER BY startTime ASC")
    fun observeOverlapping(from: Long, to: Long): Flow<List<ScrollSessionEntity>>

    @Query("SELECT * FROM scroll_sessions WHERE endTime > :from AND startTime < :to ORDER BY startTime ASC")
    suspend fun overlapping(from: Long, to: Long): List<ScrollSessionEntity>

    @Query("SELECT COALESCE(SUM(endTime - startTime), 0) FROM scroll_sessions WHERE startTime >= :from AND startTime < :to")
    suspend fun totalMsBetween(from: Long, to: Long): Long

    @Query("SELECT MIN(startTime) FROM scroll_sessions")
    suspend fun earliestStart(): Long?

    @Query("SELECT DISTINCT packageName FROM scroll_sessions")
    fun observeSeenPackages(): Flow<List<String>>

    @Query("DELETE FROM scroll_sessions WHERE endTime < :before")
    suspend fun deleteEndingBefore(before: Long): Int

    @Query("DELETE FROM scroll_sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM scroll_sessions WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String)
}
