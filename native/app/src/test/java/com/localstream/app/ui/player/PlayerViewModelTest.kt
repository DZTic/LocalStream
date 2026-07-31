package com.localstream.app.ui.player

import com.localstream.app.data.db.dao.PlaybackStateDao
import com.localstream.app.data.db.dao.WatchedItemDao
import com.localstream.app.data.db.entity.PlaybackStateEntity
import com.localstream.app.data.db.entity.WatchedItemEntity
import com.localstream.app.data.repository.VideoRepository
import com.localstream.app.data.repository.WatchStateRepository
import com.localstream.app.data.scanner.MediaScanner
import com.localstream.app.di.AppContainer
import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.SubtitleEntry
import com.localstream.app.domain.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val video1 = VideoItem(
        name = "Inception.2010.1080p.mkv",
        path = "/movies/Inception.mkv",
        duration = 8800L,
    )
    private val ep1 = VideoItem(
        name = "Breaking.Bad.S01E01.mkv",
        path = "/tv/s1e1.mkv",
        seriesName = "Breaking Bad",
        season = 1,
        episode = 1,
        duration = 2700L,
    )
    private val ep2 = VideoItem(
        name = "Breaking.Bad.S01E02.mkv",
        path = "/tv/s1e2.mkv",
        seriesName = "Breaking Bad",
        season = 1,
        episode = 2,
        duration = 2800L,
    )
    private val seriesGroup = VideoItem(
        name = "Breaking Bad",
        isSeriesGroup = true,
        isTvSeries = true,
        episodes = listOf(ep1, ep2),
    )

    private lateinit var watchedDao: FakeWatchedItemDao
    private lateinit var playbackDao: FakePlaybackStateDao
    private lateinit var watchStateRepo: WatchStateRepository
    private lateinit var videoRepo: VideoRepository
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        watchedDao = FakeWatchedItemDao()
        playbackDao = FakePlaybackStateDao()
        watchStateRepo = WatchStateRepository(watchedDao, playbackDao)
        videoRepo = VideoRepository(FakeScanner(listOf(video1, ep1, ep2), listOf(seriesGroup, video1)))
        videoRepo.scanAndLoad()

        container = AppContainer(
            overrideVideoRepository = videoRepo,
            overrideWatchStateRepository = watchStateRepo,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadVideoDetails resets position to 0 if finished past threshold`() = runTest {
        playbackDao.upsert(PlaybackStateEntity(name = video1.name, progressPct = 96.0, positionMs = 8500000L))

        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.initialPositionMs)
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `loadVideoDetails populates currentVideo and restores position`() = runTest {
        playbackDao.upsert(PlaybackStateEntity(name = video1.name, progressPct = 50.0, positionMs = 4400000L))

        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.currentVideo)
        assertEquals(video1.name, state.currentVideo?.name)
        assertEquals(4400000L, state.initialPositionMs)
    }

    @Test
    fun `onPositionChanged saves position and marks watched at 90 percent`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onPositionChanged(positionMs = 1000L, durationMs = 2000L)
        advanceUntilIdle()

        assertEquals(1000L, playbackDao.findByName(video1.name)?.positionMs)
        assertFalse(watchedDao.findByName(video1.name)?.watched ?: false)

        viewModel.onPositionChanged(positionMs = 1900L, durationMs = 2000L)
        advanceUntilIdle()

        assertTrue(watchedDao.findByName(video1.name)?.watched ?: false)
    }

    @Test
    fun `cycleAspectRatio cycles through all modes`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(AspectRatioMode.FIT, viewModel.uiState.value.aspectRatioMode)

        viewModel.cycleAspectRatio()
        advanceUntilIdle()
        assertEquals(AspectRatioMode.FILL, viewModel.uiState.value.aspectRatioMode)

        viewModel.cycleAspectRatio()
        advanceUntilIdle()
        assertEquals(AspectRatioMode.ZOOM, viewModel.uiState.value.aspectRatioMode)

        viewModel.cycleAspectRatio()
        advanceUntilIdle()
        assertEquals(AspectRatioMode.RATIO_16_9, viewModel.uiState.value.aspectRatioMode)

        viewModel.cycleAspectRatio()
        advanceUntilIdle()
        assertEquals(AspectRatioMode.RATIO_4_3, viewModel.uiState.value.aspectRatioMode)

        viewModel.cycleAspectRatio()
        advanceUntilIdle()
        assertEquals(AspectRatioMode.FIT, viewModel.uiState.value.aspectRatioMode)
    }

    @Test
    fun `cyclePlaybackSpeed cycles through speed levels`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(1.25f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(2.0f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(0.5f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(0.75f, viewModel.uiState.value.playbackSpeed)

        viewModel.cyclePlaybackSpeed()
        advanceUntilIdle()
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed)
    }

    @Test
    fun `toggleLock locks controls and hides overlay`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLocked)
        assertTrue(viewModel.uiState.value.isControlsVisible)

        viewModel.toggleLock()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)
        assertFalse(viewModel.uiState.value.isControlsVisible)

        viewModel.toggleLock()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLocked)
        assertTrue(viewModel.uiState.value.isControlsVisible)
    }

    @Test
    fun `adjustVolume and adjustBrightness emit gesture feedback`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.adjustVolume(-20)
        advanceUntilIdle()
        val volState = viewModel.uiState.value
        assertEquals(80, volState.volumePercent)
        assertEquals(FeedbackType.VOLUME, volState.gestureFeedback?.type)
        assertEquals(80, volState.gestureFeedback?.valuePercent)

        viewModel.adjustBrightness(-0.2f)
        advanceUntilIdle()
        val brightState = viewModel.uiState.value
        assertEquals(0.8f, brightState.brightnessPercent, 0.01f)
        assertEquals(FeedbackType.BRIGHTNESS, brightState.gestureFeedback?.type)

        viewModel.clearGestureFeedback()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.gestureFeedback)
    }

    @Test
    fun `seekBy updates position and emits seek feedback`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onPositionChanged(5000L, 20000L)
        advanceUntilIdle()
        viewModel.seekBy(10000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15000L, state.positionMs)
        assertEquals(FeedbackType.SEEK_FORWARD, state.gestureFeedback?.type)
        assertEquals("+10s", state.gestureFeedback?.text)
    }

    @Test
    fun `updateTracks and selectSubtitleTrack manage tracks state`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val audio = listOf(AudioTrackUiState(id = "a1", label = "Français", isSelected = true))
        val sub = listOf(SubtitleTrackUiState(id = "s1", label = "Français SRT", isSelected = false))

        viewModel.updateTracks(audio, sub)
        advanceUntilIdle()
        val state1 = viewModel.uiState.value
        assertEquals(1, state1.audioTracks.size)
        assertEquals(1, state1.subtitleTracks.size)

        viewModel.selectSubtitleTrack("s1")
        advanceUntilIdle()
        val state2 = viewModel.uiState.value
        assertEquals("s1", state2.selectedSubtitleTrackId)
        assertTrue(state2.subtitleTracks.first().isSelected)

        viewModel.addExternalSubtitle("Custom SRT", "content://sub")
        advanceUntilIdle()
        val state3 = viewModel.uiState.value
        assertEquals(2, state3.subtitleTracks.size)
        assertTrue(state3.subtitleTracks.last().isExternal)
    }

    @Test
    fun `onVideoEnded sets isEnded and marks watched`() = runTest {
        val viewModel = PlayerViewModel(video1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onVideoEnded()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEnded)
        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(watchedDao.findByName(video1.name)?.watched ?: false)
    }

    @Test
    fun `resolveNextVideo resolves next episode in series`() = runTest {
        val viewModel = PlayerViewModel(ep1.name, container)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.nextVideo)
        assertEquals(ep2.name, state.nextVideo?.name)
    }

    // -------- Fakes --------

    private class FakeScanner(
        private val raw: List<VideoItem>,
        private val grouped: List<VideoItem>,
    ) : MediaScanner {
        override fun scanVideoFiles(): List<VideoItem> = raw
        override fun scanSubtitleFiles(): List<SubtitleEntry> = emptyList()
        override fun scanAndGroup(
            whitelistedVideos: Set<String>,
            movieCollections: Map<String, MovieCollection>,
            releaseDates: Map<String, String>,
        ): List<VideoItem> = grouped
    }

    private class FakeWatchedItemDao : WatchedItemDao {
        private val map = mutableMapOf<String, WatchedItemEntity>()
        private val flow = MutableStateFlow<List<WatchedItemEntity>>(emptyList())

        override fun observeWatchedItems(): Flow<List<WatchedItemEntity>> = flow

        override suspend fun getAllWatchedItems(): List<WatchedItemEntity> = map.values.toList()

        override suspend fun upsert(item: WatchedItemEntity) {
            map[item.name] = item
            flow.value = map.values.toList()
        }

        override suspend fun upsertAll(items: List<WatchedItemEntity>) {
            items.forEach { map[it.name] = it }
            flow.value = map.values.toList()
        }

        override suspend fun deleteByName(name: String) {
            map.remove(name)
            flow.value = map.values.toList()
        }

        override suspend fun deleteAll() {
            map.clear()
            flow.value = emptyList()
        }

        override suspend fun findByName(name: String): WatchedItemEntity? = map[name]
    }

    private class FakePlaybackStateDao : PlaybackStateDao {
        private val map = mutableMapOf<String, PlaybackStateEntity>()
        private val flow = MutableStateFlow<List<PlaybackStateEntity>>(emptyList())

        override fun observeActivePlaybackStates(): Flow<List<PlaybackStateEntity>> = flow

        override suspend fun getRecentlyPlayed(limit: Int): List<PlaybackStateEntity> =
            map.values.sortedByDescending { it.lastPlayedAt }.take(limit)

        override suspend fun getAll(): List<PlaybackStateEntity> = map.values.toList()

        override suspend fun upsert(state: PlaybackStateEntity) {
            map[state.name] = state
            flow.value = map.values.toList()
        }

        override suspend fun upsertAll(states: List<PlaybackStateEntity>) {
            states.forEach { map[it.name] = it }
            flow.value = map.values.toList()
        }

        override suspend fun deleteByName(name: String) {
            map.remove(name)
            flow.value = map.values.toList()
        }

        override suspend fun deleteAll() {
            map.clear()
            flow.value = emptyList()
        }

        override suspend fun findByName(name: String): PlaybackStateEntity? = map[name]
    }
}
