package com.tveaker.app

import com.tveaker.app.data.model.*
import com.tveaker.app.data.repository.TVeakerRepository
import com.tveaker.app.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UIEdgeCasesStressTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper reproducing compactDate logic from EditorialDashboardScreen.kt
    private fun compactDate(value: String?): String {
        if (value.isNullOrBlank()) return "AT YOUR PACE"
        val date = value.substringBefore('T').split('-')
        if (date.size != 3) return value.uppercase()
        val month = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
            .getOrNull(date[1].toIntOrNull()?.minus(1) ?: -1) ?: return value.uppercase()
        return "$month ${date[2]}"
    }

    // Helper reproducing hero waterfall logic from EditorialDashboardScreen.kt
    private fun resolveFocusShow(
        activeShows: List<ShowEstimateDto>,
        selectedHeroShowId: Int?,
        nowWatching: NowWatchingDto?
    ): ShowEstimateDto? {
        return activeShows.find { it.showId == selectedHeroShowId && it.remainingEpisodes > 0 }
            ?: activeShows.find { it.showId == nowWatching?.showId && it.remainingEpisodes > 0 }
            ?: activeShows.filter { it.remainingEpisodes > 0 }.minWithOrNull(
                compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent }
            )
            ?: activeShows.firstOrNull()
    }

    @Test
    fun testCompactDate_edgeCases() {
        assertEquals("AT YOUR PACE", compactDate(null))
        assertEquals("AT YOUR PACE", compactDate(""))
        assertEquals("AT YOUR PACE", compactDate("   "))
        assertEquals("OCT 15", compactDate("2026-10-15"))
        assertEquals("OCT 15", compactDate("2026-10-15T22:30:00Z"))
        assertEquals("JAN 01", compactDate("2027-01-01"))
        assertEquals("DEC 31", compactDate("2026-12-31"))

        // Malformed / non-standard dates
        assertEquals("SOMEDATE", compactDate("somedate"))
        assertEquals("2026-15-01", compactDate("2026-15-01"))
        assertEquals("2026-00-01", compactDate("2026-00-01"))
        assertEquals("2026-XX-01", compactDate("2026-xx-01"))
    }

    @Test
    fun testDivisionByZeroPrevention_progressIndicators() {
        // Hero & picker calculation:
        // (show.watchedEpisodes.toFloat() / show.totalEpisodes.coerceAtLeast(1)).coerceIn(0f, 1f)
        val testCases = listOf(
            Triple(0, 0, 0.0f),         // 0 total, 0 watched
            Triple(0, 5, 1.0f),         // 0 total, 5 watched (overshoot)
            Triple(10, 0, 0.0f),        // 10 total, 0 watched
            Triple(10, 5, 0.5f),        // 10 total, 5 watched
            Triple(10, 10, 1.0f),       // 10 total, 10 watched
            Triple(10, 15, 1.0f),       // 10 total, 15 watched (clamped)
            Triple(-5, -2, 0.0f)        // negative edge cases
        )

        for ((total, watched, expected) in testCases) {
            val progress = (watched.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
            assertFalse("Progress must not be NaN for total=", progress.isNaN())
            assertFalse("Progress must not be Infinite for total=", progress.isInfinite())
            assertEquals("Progress for watched=, total=", expected, progress, 0.001f)
        }
    }

    @Test
    fun testCompletionPercent_boundaryConditions() {
        // ShowsScreen progress: (completionPercent / 100f).coerceIn(0f, 1f)
        val percentCases = listOf(
            0f to 0.0f,
            50f to 0.5f,
            100f to 1.0f,
            125.5f to 1.0f,   // > 100%
            -15f to 0.0f      // < 0%
        )

        for ((percent, expected) in percentCases) {
            val progress = (percent / 100f).coerceIn(0f, 1f)
            assertFalse(progress.isNaN())
            assertEquals(expected, progress, 0.001f)
        }
    }

    @Test
    fun testUnwatchedMinutes_runtimeFormatting() {
        val cases = listOf(
            0 to "0h 0m",
            45 to "0h 45m",
            60 to "1h 0m",
            125 to "2h 5m"
        )
        for ((mins, expected) in cases) {
            val formatted = "${mins / 60}h ${mins % 60}m"
            assertEquals(expected, formatted)
        }
    }

    @Test
    fun testEpisodeSeasonFormatting_edgeCases() {
        fun formatSeason(s: Int): String = "SEASON ${s.toString().padStart(2, '0')}"
        fun formatEpisode(s: Int, e: Int): String = "S${s.toString().padStart(2, '0')}E${e.toString().padStart(2, '0')}"

        assertEquals("SEASON 00", formatSeason(0))
        assertEquals("SEASON 01", formatSeason(1))
        assertEquals("SEASON 15", formatSeason(15))
        assertEquals("SEASON 105", formatSeason(105))
        assertEquals("SEASON -1", formatSeason(-1))

        assertEquals("S00E01", formatEpisode(0, 1))
        assertEquals("S02E09", formatEpisode(2, 9))
        assertEquals("S10E24", formatEpisode(10, 24))
    }

    @Test
    fun testEpisodeDrawer_groupingWithEmptyOrDiverseEpisodes() {
        val emptyList = emptyList<UnwatchedEpisodeDto>()
        val groupedEmpty = emptyList.groupBy { it.seasonNumber }
        assertTrue(groupedEmpty.isEmpty())

        val episodes = listOf(
            UnwatchedEpisodeDto(1, 0, 1, "Special 1", null, null, null),
            UnwatchedEpisodeDto(2, 1, 1, null, null, null, null),
            UnwatchedEpisodeDto(3, 1, 2, "Ep 2", "Overview", 45, "2024-01-01"),
            UnwatchedEpisodeDto(4, 2, 1, "S2 Ep 1", null, 60, null)
        )
        val grouped = episodes.groupBy { it.seasonNumber }
        assertEquals(3, grouped.size)
        assertEquals(listOf(0, 1, 2), grouped.keys.toList())
        assertEquals(1, grouped[0]?.size)
        assertEquals(2, grouped[1]?.size)
        assertEquals(1, grouped[2]?.size)
    }

    @Test
    fun testHeroWaterfall_allCompletedShows() {
        // When all shows have remainingEpisodes = 0, fallback must safely pick firstOrNull
        val completedA = ShowEstimateDto(
            showId = 1, traktId = 10, title = "Completed A", year = 2022, status = "completed",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 10, remainingEpisodes = 0, unwatchedMinutes = 0,
            completionPercent = 100f, episodesPerWeek = 0f, paceSource = "historical",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = true, nextAirDate = null
        )
        val completedB = ShowEstimateDto(
            showId = 2, traktId = 20, title = "Completed B", year = 2023, status = "completed",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 8, airedEpisodes = 8,
            unairedEpisodes = 0, watchedEpisodes = 8, remainingEpisodes = 0, unwatchedMinutes = 0,
            completionPercent = 100f, episodesPerWeek = 0f, paceSource = "historical",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = true, nextAirDate = null
        )

        val activeShows = listOf(completedA, completedB)
        val focus = resolveFocusShow(activeShows, selectedHeroShowId = null, nowWatching = null)
        assertNotNull(focus)
        assertEquals(1, focus?.showId)
    }

    @Test
    fun testHeroWaterfall_emptyActiveShows() {
        val focus = resolveFocusShow(emptyList(), selectedHeroShowId = null, nowWatching = null)
        assertNull(focus)
    }

    @Test
    fun testHeroWaterfall_selectedHeroHasZeroRemaining_fallsBackToActive() {
        val completedHero = ShowEstimateDto(
            showId = 1, traktId = 10, title = "Finished Show", year = 2022, status = "completed",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 10, remainingEpisodes = 0, unwatchedMinutes = 0,
            completionPercent = 100f, episodesPerWeek = 0f, paceSource = "historical",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = true, nextAirDate = null
        )
        val activeShow = ShowEstimateDto(
            showId = 2, traktId = 20, title = "Active Show", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 3, remainingEpisodes = 7, unwatchedMinutes = 350,
            completionPercent = 30f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = "2026-10-15", daysToFinish = 25, isCaughtUp = false, nextAirDate = null
        )

        // selectedHeroShowId = 1, but remainingEpisodes == 0 -> should fall back to activeShow (id 2)
        val focus = resolveFocusShow(listOf(completedHero, activeShow), selectedHeroShowId = 1, nowWatching = null)
        assertNotNull(focus)
        assertEquals(2, focus?.showId)
    }

    @Test
    fun testHeroWaterfall_tieBreakingByCompletionPercent() {
        val showA = ShowEstimateDto(
            showId = 1, traktId = 10, title = "Show Alpha", year = 2023, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 10, airedEpisodes = 10,
            unairedEpisodes = 0, watchedEpisodes = 5, remainingEpisodes = 5, unwatchedMinutes = 250,
            completionPercent = 50f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = false, nextAirDate = null
        )
        val showB = ShowEstimateDto(
            showId = 2, traktId = 20, title = "Show Beta", year = 2024, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 20, airedEpisodes = 20,
            unairedEpisodes = 0, watchedEpisodes = 15, remainingEpisodes = 5, unwatchedMinutes = 250,
            completionPercent = 75f, episodesPerWeek = 2f, paceSource = "historical",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = false, nextAirDate = null
        )

        // Both have remainingEpisodes = 5, but Show Beta has completionPercent 75% > 50%
        val focus = resolveFocusShow(listOf(showA, showB), selectedHeroShowId = null, nowWatching = null)
        assertEquals(2, focus?.showId)
    }

    @Test
    fun testNullArtworkAndEmptyGenresModelResilience() {
        val sparseShow = ShowEstimateDto(
            showId = 99, traktId = 990, title = "Sparse Show", year = null, status = "watching",
            statusSource = "trakt", includeSpecials = false, totalEpisodes = 5, airedEpisodes = 5,
            unairedEpisodes = 0, watchedEpisodes = 1, remainingEpisodes = 4, unwatchedMinutes = 200,
            avgRuntimeMinutes = null, remainingRuntimeDisplay = null,
            completionPercent = 20f, episodesPerWeek = 0f, paceSource = "default",
            estimatedFinishDate = null, daysToFinish = null, isCaughtUp = false, nextAirDate = null,
            posterUrl = null, backdropUrl = null, genres = emptyList()
        )

        assertNull(sparseShow.posterUrl)
        assertNull(sparseShow.backdropUrl)
        assertTrue(sparseShow.genres.isEmpty())
        assertNull(sparseShow.year)
        assertNull(sparseShow.avgRuntimeMinutes)
        assertNull(sparseShow.remainingRuntimeDisplay)
        assertNull(sparseShow.estimatedFinishDate)
        assertNull(sparseShow.daysToFinish)

        val sparseRec = RecommendationItemDto(
            candidateId = "sparse_c1", mediaType = "show", mediaItemId = 99, traktId = 990,
            title = "Sparse Rec", year = null, overview = null, genres = emptyList(),
            runtimeMinutes = null, score = 0.85f, explanation = "",
            breakdown = RecommendationRankingBreakdownDto("sparse_c1", 0.85f, 0.8f, 0.8f, 0.8f, 1f, 1f, emptyList()),
            posterUrl = null, backdropUrl = null
        )

        assertNull(sparseRec.posterUrl)
        assertNull(sparseRec.backdropUrl)
        assertTrue(sparseRec.genres.isEmpty())
        assertNull(sparseRec.runtimeMinutes)
        assertNull(sparseRec.overview)
    }
}
