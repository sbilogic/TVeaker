package com.tveaker.app.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tveaker.app.data.model.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TVeakerApiService {

    @GET("api/v1/health")
    suspend fun getHealth(): HealthDto

    @GET("api/v1/shows")
    suspend fun getShows(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int? = null
    ): List<ShowEstimateDto>

    @PATCH("api/v1/shows/{show_id}")
    suspend fun updateShow(
        @Path("show_id") showId: Int,
        @Body request: UpdateShowRequest
    ): ShowEstimateDto

    @GET("api/v1/shows/{show_id}/unwatched")
    suspend fun getUnwatchedEpisodes(
        @Path("show_id") showId: Int
    ): UnwatchedEpisodesResponseDto

    @GET("api/v1/now-watching")
    suspend fun getNowWatching(): NowWatchingDto?

    @PUT("api/v1/now-watching")
    suspend fun selectNowWatching(
        @Body request: NowWatchingSelectionRequest
    ): NowWatchingDto

    @DELETE("api/v1/now-watching")
    suspend fun clearNowWatching()

    @POST("api/v1/shows/{show_id}/quick-scrobble")
    suspend fun quickScrobble(
        @Path("show_id") showId: Int
    ): Map<String, Any>

    @POST("api/v1/shows/{show_id}/episodes/{episode_id}/watch")
    suspend fun watchEpisode(
        @Path("show_id") showId: Int,
        @Path("episode_id") episodeId: Int
    ): Map<String, Any>

    @GET("api/v1/recommendations")
    suspend fun getRecommendations(
        @Query("time_budget_minutes") timeBudgetMinutes: Int? = null,
        @Query("intent") intent: String = "auto",
        @Query("limit") limit: Int = 10
    ): RecommendationResponseDto

    @POST("api/v1/recommendations/feedback")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest
    ): Map<String, Any>

    @POST("api/v1/sync/trigger")
    suspend fun triggerSync(
        @Body request: SyncTriggerRequest
    ): SyncReportDto

    @POST("api/v1/metadata/hydrate")
    suspend fun hydrateMissingMetadata(): MetadataHydrationDto

    @GET("api/v1/app/version")
    suspend fun getAppVersion(): AppVersionDto

    @Streaming
    @GET("api/v1/app/download-apk")
    suspend fun downloadApk(): ResponseBody

    companion object {
        const val DEFAULT_BASE_URL = GatewayUrl.UNCONFIGURED_BASE_URL

        fun create(baseUrl: String = DEFAULT_BASE_URL): TVeakerApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TVeakerApiService::class.java)
        }
    }
}
