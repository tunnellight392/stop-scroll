package com.tunnellight.stop_scroll.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tunnellight.stop_scroll.data.model.FeedSurface
import com.tunnellight.stop_scroll.data.model.ScrollSession

@Entity(
    tableName = "scroll_sessions",
    indices = [Index("dayKey"), Index("packageName"), Index("endTime")],
)
data class ScrollSessionEntity(
    /**
     * Session start in epoch millis, and the primary key. Only one app is in the foreground
     * at a time, so starts are unique in practice; using it as the key lets the tracker
     * re-write ("check-point") a session that is still open without tracking a row id.
     */
    @PrimaryKey val startTime: Long,
    val endTime: Long,
    val packageName: String,
    val surface: String,
    val scrollCount: Int,
    val scrollPx: Long,
    /** Local calendar day as yyyyMMdd, for cheap day-bucketed queries. */
    val dayKey: Int,
)

fun ScrollSessionEntity.toDomain(): ScrollSession = ScrollSession(
    startTime = startTime,
    endTime = endTime,
    packageName = packageName,
    surface = FeedSurface.fromKey(surface),
    scrollCount = scrollCount,
    scrollPx = scrollPx,
)

fun ScrollSession.toEntity(dayKey: Int): ScrollSessionEntity = ScrollSessionEntity(
    startTime = startTime,
    endTime = endTime,
    packageName = packageName,
    surface = surface.name,
    scrollCount = scrollCount,
    scrollPx = scrollPx,
    dayKey = dayKey,
)
