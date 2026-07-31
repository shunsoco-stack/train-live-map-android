package com.shunsoco.trainlivemap.ui

import com.shunsoco.trainlivemap.data.local.SettingsStore
import com.shunsoco.trainlivemap.data.local.UserPreferences
import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ProviderSource
import com.shunsoco.trainlivemap.data.model.RailwaySource
import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import com.shunsoco.trainlivemap.data.repository.LoadResult
import com.shunsoco.trainlivemap.data.repository.TrainRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelPollingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `trains refresh immediately and then every seven seconds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository()
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()
            assertEquals(1, repository.trainRefreshCount)

            advanceTimeBy(MainViewModel.TRAIN_POLLING_MILLIS - 1)
            runCurrent()
            assertEquals(1, repository.trainRefreshCount)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, repository.trainRefreshCount)

            advanceTimeBy(MainViewModel.TRAIN_POLLING_MILLIS)
            runCurrent()
            assertEquals(3, repository.trainRefreshCount)

            viewModel.setForeground(false)
            runCurrent()
        }

    @Test
    fun `service status refreshes immediately and then every thirty seconds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository()
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()
            assertEquals(1, repository.serviceRefreshCount)

            advanceTimeBy(MainViewModel.SERVICE_POLLING_MILLIS - 1)
            runCurrent()
            assertEquals(1, repository.serviceRefreshCount)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, repository.serviceRefreshCount)

            viewModel.setForeground(false)
            runCurrent()
        }

    @Test
    fun `leaving foreground stops both polling loops and keeps rendered data`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository()
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()
            val trainsBeforeBackground = viewModel.state.value.trains
            val trainCallsBeforeBackground = repository.trainRefreshCount
            val serviceCallsBeforeBackground = repository.serviceRefreshCount

            viewModel.setForeground(false)
            runCurrent()
            advanceTimeBy(MainViewModel.SERVICE_POLLING_MILLIS * 2)
            runCurrent()

            assertFalse(viewModel.state.value.isForeground)
            assertNull(viewModel.state.value.nextTrainRefreshAtMillis)
            assertEquals(trainCallsBeforeBackground, repository.trainRefreshCount)
            assertEquals(serviceCallsBeforeBackground, repository.serviceRefreshCount)
            assertEquals(trainsBeforeBackground, viewModel.state.value.trains)
        }

    @Test
    fun `returning to foreground refreshes trains and service status immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository()
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()
            viewModel.setForeground(false)
            runCurrent()
            advanceTimeBy(MainViewModel.SERVICE_POLLING_MILLIS)
            runCurrent()
            val trainCallsBeforeResume = repository.trainRefreshCount
            val serviceCallsBeforeResume = repository.serviceRefreshCount

            viewModel.setForeground(true)
            runCurrent()

            assertTrue(viewModel.state.value.isForeground)
            assertEquals(trainCallsBeforeResume + 1, repository.trainRefreshCount)
            assertEquals(serviceCallsBeforeResume + 1, repository.serviceRefreshCount)
            assertNotNull(viewModel.state.value.nextTrainRefreshAtMillis)

            viewModel.setForeground(false)
            runCurrent()
        }

    @Test
    fun `returning to foreground updates the age clock synchronously`() =
        runTest(mainDispatcherRule.dispatcher) {
            var nowMillis = 1_000L
            val viewModel = MainViewModel(
                repository = FakeTrainRepository(),
                settingsStore = FakeSettingsStore(),
                dispatcher = mainDispatcherRule.dispatcher,
                clockMillis = { nowMillis },
            )
            runCurrent()

            viewModel.setForeground(true)
            assertEquals(1_000L, viewModel.state.value.nowMillis)
            viewModel.setForeground(false)

            nowMillis = 600_000L
            viewModel.setForeground(true)

            // No dispatcher turn is needed before stale train evidence expires.
            assertEquals(600_000L, viewModel.state.value.nowMillis)
            viewModel.setForeground(false)
            runCurrent()
        }

    @Test
    fun `cached snapshot and stale state survive while polling is stopped`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cachedResponse = sampleTrainsResponse(
                generatedAt = "2026-07-28T11:00:07Z",
                dataUpdatedAt = "2026-07-28T20:00:00+09:00",
                isMock = true,
                fallback = true,
            )
            val repository = FakeTrainRepository().apply {
                trainResults += LoadResult(
                    data = cachedResponse,
                    fromCache = true,
                    error = IOException("offline"),
                )
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()

            assertEquals(cachedResponse.trains, viewModel.state.value.trains)
            assertTrue(viewModel.state.value.trainFromCache)
            assertTrue(viewModel.state.value.isOffline)
            assertTrue(viewModel.state.value.isStale)
            assertTrue(viewModel.state.value.isMock)
            assertTrue(viewModel.state.value.fallback)
            assertNotNull(viewModel.state.value.trainError)
            assertEquals(cachedResponse.dataUpdatedAt, viewModel.state.value.dataUpdatedAt)

            viewModel.setForeground(false)
            runCurrent()
            advanceTimeBy(MainViewModel.SERVICE_POLLING_MILLIS)
            runCurrent()

            assertEquals(cachedResponse.trains, viewModel.state.value.trains)
            assertTrue(viewModel.state.value.trainFromCache)
            assertNotNull(viewModel.state.value.trainError)
        }

    @Test
    fun `successful live response clears an earlier fallback notice`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository().apply {
                trainResults += LoadResult(
                    data = sampleTrainsResponse(isMock = true, fallback = true),
                    fromCache = false,
                    error = null,
                )
                trainResults += LoadResult(
                    data = sampleTrainsResponse(isMock = false, fallback = false),
                    fromCache = false,
                    error = null,
                )
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()
            assertNotNull(viewModel.state.value.notice)
            assertTrue(viewModel.state.value.fallback)

            viewModel.refreshTrainsNow()

            assertNull(viewModel.state.value.notice)
            assertFalse(viewModel.state.value.isMock)
            assertFalse(viewModel.state.value.fallback)
            viewModel.setForeground(false)
        }

    @Test
    fun `service mock fallback metadata is exposed to the UI`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeTrainRepository().apply {
                serviceResults += LoadResult(
                    data = sampleServiceStatusResponse(
                        isMock = true,
                        fallback = true,
                        notice = "運行情報はフォールバックです",
                    ),
                    fromCache = false,
                    error = null,
                )
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()

            assertEquals(ProviderSource.MOCK, viewModel.state.value.serviceSource)
            assertTrue(viewModel.state.value.serviceIsMock)
            assertTrue(viewModel.state.value.serviceFallback)
            assertEquals(
                "運行情報はフォールバックです",
                viewModel.state.value.serviceNotice,
            )
            viewModel.setForeground(false)
        }

    @Test
    fun `service refresh retains all-line statuses from the API`() =
        runTest(mainDispatcherRule.dispatcher) {
            val allLineStatuses = listOf(
                ServiceStatus(
                    lineId = "tokaido",
                    lineName = "東海道線",
                    severity = ServiceSeverity.NORMAL,
                    message = "平常運転",
                    updatedAt = "2026-07-28T20:00:00+09:00",
                    dataAccuracy = DataAccuracy.ACTUAL,
                ),
                ServiceStatus(
                    lineId = "yamanote",
                    lineName = "山手線",
                    severity = ServiceSeverity.MINOR,
                    message = "一部列車に遅れがでています。",
                    updatedAt = "2026-07-28T20:00:10+09:00",
                    dataAccuracy = DataAccuracy.ACTUAL,
                ),
            )
            val repository = FakeTrainRepository().apply {
                serviceResults += LoadResult(
                    data = sampleServiceStatusResponse(serviceStatuses = allLineStatuses),
                    fromCache = false,
                    error = null,
                )
            }
            val viewModel = createViewModel(repository)
            runCurrent()

            viewModel.setForeground(true)
            runCurrent()

            assertEquals(allLineStatuses, viewModel.state.value.serviceStatuses)
            viewModel.setForeground(false)
        }

    private fun TestScope.createViewModel(repository: FakeTrainRepository): MainViewModel =
        MainViewModel(
            repository = repository,
            settingsStore = FakeSettingsStore(),
            dispatcher = mainDispatcherRule.dispatcher,
            clockMillis = { testScheduler.currentTime },
        )

    private class FakeSettingsStore(
        initial: UserPreferences = UserPreferences(visibleLineIdsInitialized = true),
    ) : SettingsStore {
        override val preferences: Flow<UserPreferences> = flowOf(initial)

        override suspend fun setFavorite(
            lineId: String,
            favorite: Boolean,
        ) = Unit

        override suspend fun setVisible(
            lineId: String,
            visible: Boolean,
        ) = Unit

        override suspend fun setVisibleLines(lineIds: Set<String>) = Unit

        override suspend fun setFavoritesOnly(enabled: Boolean) = Unit
    }

    private class FakeTrainRepository : TrainRepository {
        val trainResults = mutableListOf<LoadResult<TrainsApiResponse>>()
        val serviceResults = mutableListOf<LoadResult<ServiceStatusApiResponse>>()
        var trainRefreshCount = 0
        var serviceRefreshCount = 0
        var railwayRefreshCount = 0

        override suspend fun refreshTrains(): LoadResult<TrainsApiResponse> {
            trainRefreshCount += 1
            return if (trainResults.isNotEmpty()) {
                trainResults.removeAt(0)
            } else {
                LoadResult(
                    data = sampleTrainsResponse(),
                    fromCache = false,
                    error = null,
                )
            }
        }

        override suspend fun refreshServiceStatus(): LoadResult<ServiceStatusApiResponse> {
            serviceRefreshCount += 1
            return if (serviceResults.isNotEmpty()) {
                serviceResults.removeAt(0)
            } else {
                LoadResult(
                    data = sampleServiceStatusResponse(),
                    fromCache = false,
                    error = null,
                )
            }
        }

        override suspend fun refreshRailways(): LoadResult<RailwaysApiResponse> {
            railwayRefreshCount += 1
            return LoadResult(
                data = RailwaysApiResponse(
                    lines = emptyList(),
                    options = emptyList(),
                    generatedAt = "2026-07-28T11:00:00Z",
                    source = RailwaySource.ODPT,
                ),
                fromCache = false,
                error = null,
            )
        }
    }

    private companion object {
        fun sampleTrainsResponse(
            generatedAt: String = "2026-07-28T11:00:00Z",
            dataUpdatedAt: String = "2026-07-28T20:00:00+09:00",
            isMock: Boolean = false,
            fallback: Boolean = false,
        ) = TrainsApiResponse(
            trains = listOf(
                TrainLocation(
                    id = "train-1",
                    lineId = "tokaido",
                    lineName = "東海道線",
                    lineColor = "#f68b1e",
                    trainNumber = "",
                    direction = TrainDirection.INBOUND,
                    destination = "東京",
                    trainType = TrainType.LOCAL,
                    latitude = 35.68116,
                    longitude = 139.76713,
                    delayMinutes = 0,
                    speedKmh = 60.0,
                    status = TrainStatus.RUNNING,
                    lastUpdatedAt = dataUpdatedAt,
                    stoppedSince = null,
                    dataAccuracy = if (isMock) DataAccuracy.MOCK else DataAccuracy.ESTIMATED,
                    routeSegment = null,
                ),
            ),
            generatedAt = generatedAt,
            dataUpdatedAt = dataUpdatedAt,
            isMock = isMock,
            source = if (isMock) ProviderSource.MOCK else ProviderSource.ODPT,
            fallback = fallback,
            notice = if (fallback) "保存済みデータを表示しています" else null,
        )

        fun sampleServiceStatusResponse(
            isMock: Boolean = false,
            fallback: Boolean = false,
            notice: String? = null,
            serviceStatuses: List<ServiceStatus>? = null,
        ) = ServiceStatusApiResponse(
            serviceStatus = ServiceStatus(
                lineName = "東海道線",
                severity = ServiceSeverity.NORMAL,
                message = "平常運転",
                updatedAt = "2026-07-28T20:00:00+09:00",
                dataAccuracy = if (isMock) DataAccuracy.MOCK else DataAccuracy.ACTUAL,
            ),
            serviceStatuses = serviceStatuses,
            isMock = isMock,
            source = if (isMock) ProviderSource.MOCK else ProviderSource.ODPT,
            fallback = fallback,
            notice = notice,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
