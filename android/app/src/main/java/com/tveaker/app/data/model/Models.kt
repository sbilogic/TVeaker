package com.tveaker.app.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthDto(
    val status: String,
    val timestamp: String,
    @Json(name = "database_connected") val databaseConnected: Boolean,
    @Json(name = "trakt_authenticated") val traktAuthenticated: Boolean,
    val username: String?,
    @Json(name = "last_sync_at") val lastSyncAt: String?
)

@JsonClass(generateAdapter = true)
data class ShowEstimateDto(
    @Json(name = "show_id") val showId: Int,
    @Json(name = "trakt_id") val traktId: Int,
    val title: String,
    val year: Int?,
    val status: String,
    @Json(name = "status_source") val statusSource: String,
    @Json(name = "include_specials") val includeSpecials: Boolean = false,
    @Json(name = "total_episodes") val totalEpisodes: Int,
    @Json(name = "aired_episodes") val airedEpisodes: Int,
    @Json(name = "unaired_episodes") val unairedEpisodes: Int = 0,
    @Json(name = "watched_episodes") val watchedEpisodes: Int,
    @Json(name = "remaining_episodes") val remainingEpisodes: Int,
    @Json(name = "unwatched_minutes") val unwatchedMinutes: Int,
    @Json(name = "avg_runtime_minutes") val avgRuntimeMinutes: Int? = null,
    @Json(name = "remaining_runtime_display") val remainingRuntimeDisplay: String? = null,
    @Json(name = "completion_percent") val completionPercent: Float,
    @Json(name = "episodes_per_week") val episodesPerWeek: Float,
    @Json(name = "pace_source") val paceSource: String,
    @Json(name = "estimated_finish_date") val estimatedFinishDate: String?,
    @Json(name = "days_to_finish") val daysToFinish: Int?,
    @Json(name = "is_caught_up") val isCaughtUp: Boolean,
    @Json(name = "next_air_date") val nextAirDate: String?,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "backdrop_url") val backdropUrl: String? = null,
    val genres: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UnwatchedEpisodeDto(
    val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    val title: String?,
    val overview: String?,
    @Json(name = "runtime_minutes") val runtimeMinutes: Int?,
    @Json(name = "first_aired") val firstAired: String?
)

@JsonClass(generateAdapter = true)
data class UnwatchedEpisodesResponseDto(
    @Json(name = "show_id") val showId: Int,
    val title: String,
    val year: Int?,
    @Json(name = "poster_url") val posterUrl: String?,
    @Json(name = "backdrop_url") val backdropUrl: String?,
    val genres: List<String> = emptyList(),
    @Json(name = "total_episodes") val totalEpisodes: Int,
    @Json(name = "aired_episodes") val airedEpisodes: Int = totalEpisodes,
    @Json(name = "unaired_episodes") val unairedEpisodes: Int = 0,
    @Json(name = "watched_episodes") val watchedEpisodes: Int,
    @Json(name = "remaining_episodes") val remainingEpisodes: Int,
    @Json(name = "unwatched_minutes") val unwatchedMinutes: Int,
    @Json(name = "next_air_date") val nextAirDate: String? = null,
    @Json(name = "unwatched_episodes") val unwatchedEpisodes: List<UnwatchedEpisodeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NowWatchingDto(
    @Json(name = "show_id") val showId: Int,
    @Json(name = "show_title") val showTitle: String,
    @Json(name = "episode_id") val episodeId: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "episode_title") val episodeTitle: String?,
    @Json(name = "runtime_minutes") val runtimeMinutes: Int?
)

@JsonClass(generateAdapter = true)
data class AppVersionDto(
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "version_name") val versionName: String,
    @Json(name = "apk_url") val apkUrl: String,
    val changelog: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "apk_size_bytes") val apkSizeBytes: Long? = null
)

@JsonClass(generateAdapter = true)
data class RecommendationRankingBreakdownDto(
    @Json(name = "candidate_id") val candidateId: String,
    @Json(name = "final_score") val finalScore: Float,
    @Json(name = "content_score") val contentScore: Float,
    @Json(name = "source_score") val sourceScore: Float,
    @Json(name = "progress_score") val progressScore: Float,
    @Json(name = "budget_score") val budgetScore: Float,
    @Json(name = "intent_score") val intentScore: Float,
    @Json(name = "matched_genres") val matchedGenres: List<String>
)

@JsonClass(generateAdapter = true)
data class RecommendationItemDto(
    @Json(name = "candidate_id") val candidateId: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "media_item_id") val mediaItemId: Int,
    @Json(name = "trakt_id") val traktId: Int,
    val title: String,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    @Json(name = "runtime_minutes") val runtimeMinutes: Int?,
    val score: Float,
    val explanation: String,
    val breakdown: RecommendationRankingBreakdownDto,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "backdrop_url") val backdropUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class RecommendationResponseDto(
    @Json(name = "run_id") val runId: Int,
    val items: List<RecommendationItemDto>
)

@JsonClass(generateAdapter = true)
data class UpdateShowRequest(
    val status: String? = null,
    @Json(name = "include_specials") val includeSpecials: Boolean? = null,
    @Json(name = "manual_episodes_per_week") val manualEpisodesPerWeek: Float? = null
)

@JsonClass(generateAdapter = true)
data class NowWatchingSelectionRequest(
    @Json(name = "episode_id") val episodeId: Int
)

@JsonClass(generateAdapter = true)
data class FeedbackRequest(
    @Json(name = "run_id") val runId: Int,
    @Json(name = "candidate_id") val candidateId: String,
    val action: String
)

@JsonClass(generateAdapter = true)
data class SyncTriggerRequest(
    val mode: String = "incremental"
)

@JsonClass(generateAdapter = true)
data class SyncReportDto(
    val status: String,
    val fetched: Map<String, Int>? = null,
    val duration_ms: Float? = null
)

@JsonClass(generateAdapter = true)
data class MetadataHydrationDto(
    val status: String,
    val limit: Int
)
