package com.shunsoco.trainlivemap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shunsoco.trainlivemap.data.local.SettingsStore
import com.shunsoco.trainlivemap.data.local.UserPreferences
import com.shunsoco.trainlivemap.data.model.ProviderSource
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import com.shunsoco.trainlivemap.data.model.RailwaySource
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.repository.LoadResult
import com.shunsoco.trainlivemap.data.repository.TrainRepository
import com.shunsoco.trainlivemap.domain.service.serviceStatusWithTrainDelayFallback
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val trains: List<TrainLocation> = emptyList(),
    val railwayLines: List<RailwayMapLine> = emptyList(),
    val railwayOptions: List<RailwayFilterOption> = emptyList(),
    val serviceStatus: ServiceStatus? = null,
    val serviceStatuses: List<ServiceStatus> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val statusFilter: TrainStatusFilter = TrainStatusFilter.ALL,
    val selectedTrainId: String? = null,
    val trainsLoading: Boolean = false,
    val serviceLoading: Boolean = false,
    val railwaysLoading: Boolean = true,
    val trainFromCache: Boolean = false,
    val serviceFromCache: Boolean = false,
    val railwaysFromCache: Boolean = false,
    val trainError: String? = null,
    val serviceError: String? = null,
    val railwaysError: String? = null,
    val generatedAt: String? = null,
    val dataUpdatedAt: String? = null,
    val trainSource: ProviderSource? = null,
    val serviceSource: ProviderSource? = null,
    val railwaySource: RailwaySource? = null,
    val isMock: Boolean = false,
    val fallback: Boolean = false,
    val notice: String? = null,
    val serviceIsMock: Boolean = false,
    val serviceFallback: Boolean = false,
    val serviceNotice: String? = null,
    val isForeground: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
    val nextTrainRefreshAtMillis: Long? = null,
) {
    val selectedTrain: TrainLocation?
        get() = trains.firstOrNull { it.id == selectedTrainId }

    /**
     * Keep the API status untouched and derive only what is rendered. This
     * lets the estimate disappear as soon as its train snapshot becomes old.
     */
    val effectiveServiceStatuses: List<ServiceStatus>
        get() = serviceStatuses
            .ifEmpty { listOfNotNull(serviceStatus) }
            .map { officialStatus ->
            serviceStatusWithTrainDelayFallback(
                serviceStatus = officialStatus,
                trains = trains,
                nowMillis = nowMillis,
            )
        }

    /**
     * The compact map UI renders the most important status for the lines the
     * user currently displays. The full API array is still retained and every
     * line receives its own train-delay fallback before this selection.
     */
    val effectiveServiceStatus: ServiceStatus?
        get() {
            val allStatuses = effectiveServiceStatuses
            if (allStatuses.isEmpty()) return null

            val displayedLineIds = if (preferences.visibleLineIdsInitialized) {
                if (preferences.favoritesOnly) {
                    preferences.visibleLineIds intersect preferences.favoriteLineIds
                } else {
                    preferences.visibleLineIds
                }
            } else {
                null
            }
            val visibleStatuses = displayedLineIds?.let { lineIds ->
                allStatuses.filter { status -> status.lineId in lineIds }
            }
            if (visibleStatuses != null) {
                return visibleStatuses.maxByOrNull { status -> status.severity.displayPriority }
            }

            val compatibilityStatuses = serviceStatus?.lineId?.let { lineId ->
                allStatuses.filter { status -> status.lineId == lineId }
            }.orEmpty()
            val candidates = compatibilityStatuses
                .ifEmpty { allStatuses }

            return candidates.maxByOrNull { status -> status.severity.displayPriority }
        }

    val isOffline: Boolean
        get() = trainFromCache ||
            serviceFromCache ||
            railwaysFromCache ||
            trainError != null ||
            serviceError != null ||
            railwaysError != null

    val isStale: Boolean
        get() = trainFromCache || dataUpdatedAt.isOlderThan(nowMillis, STALE_AFTER_MILLIS)

    val nextRefreshSeconds: Int?
        get() = nextTrainRefreshAtMillis?.let { refreshAt ->
            (((refreshAt - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
        }
}

class MainViewModel(
    private val repository: TrainRepository,
    private val settingsStore: SettingsStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val trainPollingMillis: Long = TRAIN_POLLING_MILLIS,
    private val servicePollingMillis: Long = SERVICE_POLLING_MILLIS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState(nowMillis = clockMillis()))
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    private val trainRefreshMutex = Mutex()
    private val serviceRefreshMutex = Mutex()
    private val railwayRefreshMutex = Mutex()

    private var trainPollingJob: Job? = null
    private var servicePollingJob: Job? = null
    private var clockJob: Job? = null

    init {
        viewModelScope.launch(dispatcher) {
            settingsStore.preferences.collect { preferences ->
                mutableState.update { it.copy(preferences = preferences) }
            }
        }
        refreshRailways()
    }

    /**
     * Called from the Activity lifecycle. Entering foreground always performs
     * an immediate fetch; leaving foreground cancels both polling loops.
     */
    fun setForeground(foreground: Boolean) {
        if (mutableState.value.isForeground == foreground) return
        val foregroundNowMillis = if (foreground) clockMillis() else null
        mutableState.update {
            it.copy(
                isForeground = foreground,
                nowMillis = foregroundNowMillis ?: it.nowMillis,
                nextTrainRefreshAtMillis = if (foreground) it.nextTrainRefreshAtMillis else null,
            )
        }
        if (foreground) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    fun retryAll() {
        viewModelScope.launch(dispatcher) {
            coroutineScope {
                launch { refreshTrainsNow() }
                launch { refreshServiceStatusNow() }
                launch { refreshRailwaysNow() }
            }
        }
    }

    fun setStatusFilter(filter: TrainStatusFilter) {
        mutableState.update { it.copy(statusFilter = filter) }
    }

    fun selectTrain(trainId: String?) {
        mutableState.update { it.copy(selectedTrainId = trainId) }
    }

    fun setFavorite(
        lineId: String,
        favorite: Boolean,
    ) {
        viewModelScope.launch(dispatcher) {
            settingsStore.setFavorite(lineId, favorite)
        }
    }

    fun setVisible(
        lineId: String,
        visible: Boolean,
    ) {
        viewModelScope.launch(dispatcher) {
            settingsStore.setVisible(lineId, visible)
        }
    }

    fun setVisibleLines(lineIds: Set<String>) {
        viewModelScope.launch(dispatcher) {
            settingsStore.setVisibleLines(lineIds)
        }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch(dispatcher) {
            settingsStore.setFavoritesOnly(enabled)
        }
    }

    private fun refreshRailways() {
        viewModelScope.launch(dispatcher) {
            refreshRailwaysNow()
        }
    }

    private fun startPolling() {
        stopPolling()
        clockJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                mutableState.update { it.copy(nowMillis = clockMillis()) }
                delay(CLOCK_TICK_MILLIS)
            }
        }
        trainPollingJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                refreshTrainsNow()
                val refreshAt = clockMillis() + trainPollingMillis
                mutableState.update { it.copy(nextTrainRefreshAtMillis = refreshAt) }
                delay(trainPollingMillis)
            }
        }
        servicePollingJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                refreshServiceStatusNow()
                delay(servicePollingMillis)
            }
        }
    }

    private fun stopPolling() {
        trainPollingJob?.cancel()
        trainPollingJob = null
        servicePollingJob?.cancel()
        servicePollingJob = null
        clockJob?.cancel()
        clockJob = null
        mutableState.update { it.copy(nextTrainRefreshAtMillis = null) }
    }

    internal suspend fun refreshTrainsNow() {
        trainRefreshMutex.withLock {
            mutableState.update { it.copy(trainsLoading = it.trains.isEmpty()) }
            val result = repository.refreshTrains()
            mutableState.update { previous ->
                val response = result.data
                previous.copy(
                    trains = response?.trains ?: previous.trains,
                    trainsLoading = false,
                    trainFromCache = result.fromCache,
                    trainError = result.userFacingError("列車情報の更新に失敗しました"),
                    generatedAt = response?.generatedAt ?: previous.generatedAt,
                    dataUpdatedAt = response?.dataUpdatedAt ?: previous.dataUpdatedAt,
                    trainSource = response?.source ?: previous.trainSource,
                    isMock = response?.isMock ?: previous.isMock,
                    fallback = response?.fallback ?: previous.fallback,
                    notice = if (response != null) response.notice else previous.notice,
                    selectedTrainId = previous.selectedTrainId?.takeIf { selectedId ->
                        (response?.trains ?: previous.trains).any { it.id == selectedId }
                    },
                )
            }
        }
    }

    internal suspend fun refreshServiceStatusNow() {
        serviceRefreshMutex.withLock {
            mutableState.update {
                it.copy(serviceLoading = it.serviceStatus == null && it.serviceStatuses.isEmpty())
            }
            val result = repository.refreshServiceStatus()
            mutableState.update { previous ->
                val response = result.data
                previous.copy(
                    serviceStatus = response?.serviceStatus ?: previous.serviceStatus,
                    serviceStatuses = if (response != null) {
                        response.serviceStatuses.orEmpty().ifEmpty { listOf(response.serviceStatus) }
                    } else {
                        previous.serviceStatuses
                    },
                    serviceLoading = false,
                    serviceFromCache = result.fromCache,
                    serviceError = result.userFacingError("運行情報の更新に失敗しました"),
                    serviceSource = response?.source ?: previous.serviceSource,
                    serviceIsMock = response?.isMock ?: previous.serviceIsMock,
                    serviceFallback = response?.fallback ?: previous.serviceFallback,
                    serviceNotice = if (response != null) {
                        response.notice
                    } else {
                        previous.serviceNotice
                    },
                )
            }
        }
    }

    internal suspend fun refreshRailwaysNow() {
        railwayRefreshMutex.withLock {
            mutableState.update { it.copy(railwaysLoading = it.railwayLines.isEmpty()) }
            val result = repository.refreshRailways()
            mutableState.update { previous ->
                val response = result.data
                previous.copy(
                    railwayLines = response?.lines ?: previous.railwayLines,
                    railwayOptions = response?.options ?: previous.railwayOptions,
                    railwaysLoading = false,
                    railwaysFromCache = result.fromCache,
                    railwaysError = result.userFacingError("路線情報の更新に失敗しました"),
                    railwaySource = response?.source ?: previous.railwaySource,
                )
            }
            initializeVisibleLinesIfNeeded(result)
        }
    }

    private suspend fun initializeVisibleLinesIfNeeded(
        result: LoadResult<com.shunsoco.trainlivemap.data.model.RailwaysApiResponse>,
    ) {
        val options = result.data?.options ?: return
        val available = options.filter(RailwayFilterOption::available)
        val initial = when {
            available.any { it.id == TOKAIDO_LINE_ID } -> setOf(TOKAIDO_LINE_ID)
            available.isNotEmpty() -> setOf(available.first().id)
            else -> emptySet()
        }
        // The store checks and writes the initialized flag in one atomic
        // DataStore edit, avoiding a race with the first preferences emission.
        settingsStore.initializeVisibleLines(initial)
    }

    companion object {
        const val TRAIN_POLLING_MILLIS = 7_000L
        const val SERVICE_POLLING_MILLIS = 30_000L
        private const val CLOCK_TICK_MILLIS = 1_000L
        private const val TOKAIDO_LINE_ID = "tokaido"
    }
}

class MainViewModelFactory(
    private val repository: TrainRepository,
    private val settingsStore: SettingsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(repository, settingsStore) as T
    }
}

private fun LoadResult<*>.userFacingError(message: String): String? =
    error?.let { message }

private fun String?.isOlderThan(
    nowMillis: Long,
    thresholdMillis: Long,
): Boolean {
    val value = this ?: return false
    val timestamp = runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
        ?: return false
    return nowMillis - timestamp > thresholdMillis
}

private const val STALE_AFTER_MILLIS = 90_000L

private val ServiceSeverity.displayPriority: Int
    get() = when (this) {
        ServiceSeverity.NORMAL -> 0
        ServiceSeverity.MINOR -> 1
        ServiceSeverity.MAJOR -> 2
    }
