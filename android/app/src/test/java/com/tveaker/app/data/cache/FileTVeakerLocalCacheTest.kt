package com.tveaker.app.data.cache

import com.tveaker.app.data.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTVeakerLocalCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleShow(
        id: Int,
        title: String,
        status: String = "watching"
    ) = ShowEstimateDto(
        showId = id,
        traktId = id * 10,
        title = title,
        year = 2024,
        status = status,
        statusSource = "trakt",
        includeSpecials = false,
        totalEpisodes = 10,
        airedEpisodes = 10,
        unairedEpisodes = 0,
        watchedEpisodes = 4,
        remainingEpisodes = 6,
        unwatchedMinutes = 300,
        completionPercent = 40f,
        episodesPerWeek = 2f,
        paceSource = "historical",
        estimatedFinishDate = "2026-10-01",
        daysToFinish = 21,
        isCaughtUp = false,
        nextAirDate = null
    )

    private fun sampleNowWatching(showId: Int = 1) = NowWatchingDto(
        showId = showId,
        showTitle = "Show $showId",
        episodeId = showId * 100 + 1,
        seasonNumber = 1,
        episodeNumber = 5,
        episodeTitle = "Episode 5",
        runtimeMinutes = 50
    )

    private fun sampleUnwatched(showId: Int = 1) = UnwatchedEpisodesResponseDto(
        showId = showId,
        title = "Show $showId",
        year = 2024,
        posterUrl = null,
        backdropUrl = null,
        totalEpisodes = 10,
        watchedEpisodes = 4,
        remainingEpisodes = 6,
        unwatchedMinutes = 300,
        unwatchedEpisodes = listOf(
            UnwatchedEpisodeDto(
                id = showId * 100 + 5,
                seasonNumber = 1,
                episodeNumber = 5,
                title = "Episode 5",
                overview = "Exciting episode",
                runtimeMinutes = 50,
                firstAired = "2024-05-01"
            )
        )
    )

    @Test
    fun testShowsCacheAndStatusFiltering() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        assertNull("Initially shows should be null", cache.getShows())

        val shows = listOf(
            sampleShow(1, "Show A", "watching"),
            sampleShow(2, "Show B", "completed"),
            sampleShow(3, "Show C", "watching")
        )
        cache.saveShows(shows)

        val all = cache.getShows()
        assertNotNull(all)
        assertEquals(3, all!!.size)

        val watching = cache.getShows("watching")
        assertNotNull(watching)
        assertEquals(2, watching!!.size)
        assertTrue(watching.all { it.status == "watching" })

        val completed = cache.getShows("completed")
        assertNotNull(completed)
        assertEquals(1, completed!!.size)
        assertEquals("Show B", completed[0].title)

        val planned = cache.getShows("planned")
        assertNotNull(planned)
        assertTrue(planned!!.isEmpty())
    }

    @Test
    fun testNowWatchingCaching() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        assertFalse(cache.hasNowWatching())
        assertNull(cache.getNowWatching())

        val nowWatching = sampleNowWatching(42)
        cache.saveNowWatching(nowWatching)

        assertTrue(cache.hasNowWatching())
        assertEquals(42, cache.getNowWatching()?.showId)

        // Test caching null explicitly
        cache.saveNowWatching(null)
        assertTrue(cache.hasNowWatching())
        assertNull(cache.getNowWatching())
    }

    @Test
    fun testUnwatchedEpisodesCaching() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        assertNull(cache.getUnwatchedEpisodes(1))

        val episodes1 = sampleUnwatched(1)
        val episodes2 = sampleUnwatched(2)
        cache.saveUnwatchedEpisodes(1, episodes1)
        cache.saveUnwatchedEpisodes(2, episodes2)

        val cached1 = cache.getUnwatchedEpisodes(1)
        assertNotNull(cached1)
        assertEquals(1, cached1!!.showId)
        assertEquals(1, cached1.unwatchedEpisodes.size)

        val cached2 = cache.getUnwatchedEpisodes(2)
        assertNotNull(cached2)
        assertEquals(2, cached2!!.showId)

        assertNull(cache.getUnwatchedEpisodes(99))
    }

    @Test
    fun testPersistenceAcrossRestarts() = runTest {
        val dir = tempFolder.root
        val cache1 = FileTVeakerLocalCache(dir)

        cache1.saveShows(listOf(sampleShow(10, "Persistent Show", "watching")))
        cache1.saveNowWatching(sampleNowWatching(10))
        cache1.saveUnwatchedEpisodes(10, sampleUnwatched(10))

        // Create a completely new cache instance pointing to the same directory
        val cache2 = FileTVeakerLocalCache(dir)

        val shows = cache2.getShows()
        assertNotNull("Shows must persist across restart", shows)
        assertEquals(1, shows!!.size)
        assertEquals("Persistent Show", shows[0].title)

        assertTrue(cache2.hasNowWatching())
        assertEquals(10, cache2.getNowWatching()?.showId)

        val unwatched = cache2.getUnwatchedEpisodes(10)
        assertNotNull("Unwatched episodes must persist across restart", unwatched)
        assertEquals(10, unwatched!!.showId)
    }

    @Test
    fun testUpdateShow() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        val show = sampleShow(5, "Original Title", "watching")
        cache.saveShows(listOf(show))

        val updated = show.copy(title = "Updated Title", remainingEpisodes = 2)
        cache.updateShow(updated)

        val shows = cache.getShows()
        assertNotNull(shows)
        assertEquals("Updated Title", shows!![0].title)
        assertEquals(2, shows[0].remainingEpisodes)
    }

    @Test
    fun testClear() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        cache.saveShows(listOf(sampleShow(1, "Show")))
        cache.saveNowWatching(sampleNowWatching(1))

        cache.clear()

        assertNull(cache.getShows())
        assertFalse(cache.hasNowWatching())
        assertNull(cache.getNowWatching())
    }

    @Test
    fun testUpdateShow_updatesStatusFilterAccurately() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val show = sampleShow(10, "Status Show", "watching")
        cache.saveShows(listOf(show))

        assertEquals(1, cache.getShows("watching")?.size)
        assertEquals(0, cache.getShows("paused")?.size)

        // Update show to paused
        val updated = show.copy(status = "paused")
        cache.updateShow(updated)

        // In-memory verification
        val watchingAfter = cache.getShows("watching")
        assertEquals(0, watchingAfter?.size)
        val pausedAfter = cache.getShows("paused")
        assertEquals(1, pausedAfter?.size)
        assertEquals(10, pausedAfter?.get(0)?.showId)

        // Persistence across restart verification
        val cache2 = FileTVeakerLocalCache(dir)
        val watchingPersisted = cache2.getShows("watching")
        assertEquals(0, watchingPersisted?.size)
        val pausedPersisted = cache2.getShows("paused")
        assertEquals(1, pausedPersisted?.size)
        assertEquals("Status Show", pausedPersisted?.get(0)?.title)
    }

    @Test
    fun testSaveShowsPartialStatus_doesNotTruncateOtherShows() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        val shows = listOf(
            sampleShow(1, "Show 1", "watching"),
            sampleShow(2, "Show 2", "watching"),
            sampleShow(3, "Show 3", "completed")
        )
        // Master list cached
        cache.saveShows(shows)
        assertEquals(2, cache.getShows("watching")?.size)

        // Partial update from dashboard (only 1 watching show returned)
        val partial = listOf(sampleShow(1, "Show 1 Refreshed", "watching"))
        cache.saveShows(partial, status = "watching")

        // Should still contain all watching shows, with Show 1 refreshed
        val watching = cache.getShows("watching")
        assertEquals(2, watching?.size)
        assertEquals("Show 1 Refreshed", watching?.firstOrNull { it.showId == 1 }?.title)
        assertEquals("Show 2", watching?.firstOrNull { it.showId == 2 }?.title)
        assertEquals(1, cache.getShows("completed")?.size)
    }

    @Test
    fun testRecommendationsCaching() = runTest {
        val dir = tempFolder.root
        val cache1 = FileTVeakerLocalCache(dir)
        val recs = RecommendationResponseDto(
            runId = 5,
            items = listOf(
                RecommendationItemDto(
                    candidateId = "cand_1",
                    mediaType = "show",
                    mediaItemId = 101,
                    traktId = 1010,
                    title = "Recommended Show",
                    year = 2024,
                    overview = "Show overview",
                    genres = listOf("Sci-Fi"),
                    runtimeMinutes = 50,
                    score = 0.95f,
                    explanation = "High score match",
                    breakdown = RecommendationRankingBreakdownDto(
                        candidateId = "cand_1",
                        finalScore = 0.95f,
                        contentScore = 0.9f,
                        sourceScore = 0.8f,
                        progressScore = 0.7f,
                        budgetScore = 0.9f,
                        intentScore = 0.85f,
                        matchedGenres = listOf("Sci-Fi")
                    )
                )
            )
        )
        cache1.saveRecommendations(recs)

        val cached1 = cache1.getRecommendations()
        assertNotNull(cached1)
        assertEquals(5, cached1?.runId)
        assertEquals(1, cached1?.items?.size)

        // Persists across restart
        val cache2 = FileTVeakerLocalCache(dir)
        val cached2 = cache2.getRecommendations()
        assertNotNull(cached2)
        assertEquals(5, cached2?.runId)
        assertEquals("Recommended Show", cached2?.items?.firstOrNull()?.title)
    }

    @Test
    fun testSaveShowsStatusFilter_removesStaleShowsWhenNotPartial() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        val shows = listOf(
            sampleShow(1, "Watching 1", "watching"),
            sampleShow(2, "Watching 2", "watching"),
            sampleShow(3, "Completed 1", "completed")
        )
        cache.saveShows(shows)
        assertEquals(2, cache.getShows("watching")?.size)
        assertEquals(1, cache.getShows("completed")?.size)

        // Full refresh of "watching" status returns only Watching 1 (Watching 2 was finished on server)
        val fullWatchingUpdate = listOf(sampleShow(1, "Watching 1", "watching"))
        cache.saveShows(fullWatchingUpdate, status = "watching", isPartial = false)

        val watching = cache.getShows("watching")
        assertEquals(1, watching?.size)
        assertEquals(1, watching?.firstOrNull()?.showId)

        // Completed shows are untouched
        val completed = cache.getShows("completed")
        assertEquals(1, completed?.size)
        assertEquals("Completed 1", completed?.firstOrNull()?.title)
    }

    @Test
    fun testSaveShowsAllWithLimit_doesNotTruncateOtherShows() = runTest {
        val cache = FileTVeakerLocalCache(tempFolder.root)
        val shows = listOf(
            sampleShow(1, "Show 1"),
            sampleShow(2, "Show 2"),
            sampleShow(3, "Show 3")
        )
        cache.saveShows(shows)
        assertEquals(3, cache.getShows()?.size)

        // Partial query without status (e.g. limit=1)
        val partialAll = listOf(sampleShow(1, "Show 1 Updated"))
        cache.saveShows(partialAll, status = null, isPartial = true)

        // Should NOT have cleared the other shows
        val all = cache.getShows()
        assertEquals(3, all?.size)
        assertEquals("Show 1 Updated", all?.firstOrNull { it.showId == 1 }?.title)
        assertEquals("Show 2", all?.firstOrNull { it.showId == 2 }?.title)
    }

    @Test
    fun testTempFileRecovery_whenMainFileMissing() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val shows = listOf(sampleShow(10, "Recovered Show"))
        cache.saveShows(shows)

        // Simulate crash right after writing .tmp before rename finished:
        val mainFile = java.io.File(dir, "shows.json")
        val tmpFile = java.io.File(dir, "shows.json.tmp")
        mainFile.renameTo(tmpFile)
        assertFalse(mainFile.exists())
        assertTrue(tmpFile.exists())

        // New cache instance reading from directory
        val cache2 = FileTVeakerLocalCache(dir)
        val recovered = cache2.getShows()
        assertNotNull("Should recover from .tmp file if main file is missing", recovered)
        assertEquals(1, recovered?.size)
        assertEquals("Recovered Show", recovered?.firstOrNull()?.title)
    }

    @Test
    fun testUnwatchedEpisodes_tempFileRecovery() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val unwatched = sampleUnwatched(77)
        cache.saveUnwatchedEpisodes(77, unwatched)

        // Simulate crash right after writing .tmp before rename
        val epDir = java.io.File(dir, "episodes")
        val mainFile = java.io.File(epDir, "77.json")
        val tmpFile = java.io.File(epDir, "77.json.tmp")
        mainFile.renameTo(tmpFile)
        assertFalse(mainFile.exists())
        assertTrue(tmpFile.exists())

        val cache2 = FileTVeakerLocalCache(dir)
        val recovered = cache2.getUnwatchedEpisodes(77)
        assertNotNull("Should recover unwatched episodes from .tmp file if main file is missing", recovered)
        assertEquals(77, recovered?.showId)
        assertEquals(1, recovered?.unwatchedEpisodes?.size)
    }

    @Test
    fun testNowWatching_tempFileRecovery() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val nowWatching = sampleNowWatching(88)
        cache.saveNowWatching(nowWatching)

        val mainFile = java.io.File(dir, "now_watching.json")
        val tmpFile = java.io.File(dir, "now_watching.json.tmp")
        mainFile.renameTo(tmpFile)
        assertFalse(mainFile.exists())
        assertTrue(tmpFile.exists())

        val cache2 = FileTVeakerLocalCache(dir)
        val recovered = cache2.getNowWatching()
        assertNotNull("Should recover now watching from .tmp file", recovered)
        assertEquals(88, recovered?.showId)
    }

    @Test
    fun testRecommendations_tempFileRecovery() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val recs = RecommendationResponseDto(runId = 99, items = emptyList())
        cache.saveRecommendations(recs)

        val mainFile = java.io.File(dir, "recommendations.json")
        val tmpFile = java.io.File(dir, "recommendations.json.tmp")
        mainFile.renameTo(tmpFile)
        assertFalse(mainFile.exists())
        assertTrue(tmpFile.exists())

        val cache2 = FileTVeakerLocalCache(dir)
        val recovered = cache2.getRecommendations()
        assertNotNull("Should recover recommendations from .tmp file", recovered)
        assertEquals(99, recovered?.runId)
    }

    @Test
    fun testCorruptedMainFile_recoversFromTempFileIfPresent() = runTest {
        val dir = tempFolder.root
        val cache = FileTVeakerLocalCache(dir)
        val shows = listOf(sampleShow(55, "Safe Show"))
        cache.saveShows(shows)

        val mainFile = java.io.File(dir, "shows.json")
        val tmpFile = java.io.File(dir, "shows.json.tmp")
        // Create a valid tmp file and corrupt the main file
        mainFile.copyTo(tmpFile, overwrite = true)
        mainFile.writeText("{corrupt: truncated json")

        val cache2 = FileTVeakerLocalCache(dir)
        val recovered = cache2.getShows()
        assertNotNull("Should recover from valid .tmp file when main file is corrupted", recovered)
        assertEquals(1, recovered?.size)
        assertEquals("Safe Show", recovered?.firstOrNull()?.title)
    }
}
