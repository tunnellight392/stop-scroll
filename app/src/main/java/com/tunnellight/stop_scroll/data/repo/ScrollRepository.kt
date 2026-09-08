package com.tunnellight.stop_scroll.data.repo

import com.tunnellight.stop_scroll.data.db.ScrollSessionDao
import com.tunnellight.stop_scroll.data.db.toDomain
import com.tunnellight.stop_scroll.data.db.toEntity
import com.tunnellight.stop_scroll.data.model.ScrollSession
import com.tunnellight.stop_scroll.data.model.dayKeyOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScrollRepository(private val dao: ScrollSessionDao) {

    fun observe(from: Long, to: Long): Flow<List<ScrollSession>> =
        dao.observeOverlapping(from, to).map { rows -> rows.map { it.toDomain() } }

    suspend fun sessions(from: Long, to: Long): List<ScrollSession> =
        dao.overlapping(from, to).map { it.toDomain() }

    /**
     * Writes a session. Called both to check-point a bout that is still running and to close
     * it; because the row is keyed by start time the second write replaces the first rather
     * than duplicating it, so a service restart mid-bout costs at most the last check-point
     * interval instead of the whole session.
     */
    suspend fun persist(session: ScrollSession, zone: ZoneId = ZoneId.systemDefault()) {
        val day = dayKeyOf(Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalDate())
        dao.upsert(session.toEntity(day))
    }

    suspend fun totalBetween(from: Long, to: Long): Long = dao.totalMsBetween(from, to)

    fun observeSeenPackages(): Flow<List<String>> = dao.observeSeenPackages()

    suspend fun earliestDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        dao.earliestStart()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    suspend fun prune(retentionDays: Int, nowMs: Long): Int {
        if (retentionDays <= 0) return 0
        return dao.deleteEndingBefore(nowMs - retentionDays * 86_400_000L)
    }

    suspend fun clearAll() = dao.deleteAll()

    suspend fun clearApp(packageName: String) = dao.deleteForPackage(packageName)
}
