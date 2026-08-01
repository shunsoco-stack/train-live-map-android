package com.shunsoco.trainlivemap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shunsoco.trainlivemap.data.local.SettingsStore
import com.shunsoco.trainlivemap.data.local.UserPreferences
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.model.ProviderSource
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import com.shunsoco.trainlivemap.data.model.RailwaySource
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.repository.LoadResult
import com.shunsoco.trainlivemap.data.repository.CommunityReportRepository
import com.shunsoco.trainlivemap.data.repository.TrainRepository
import com.shunsoco.trainlivemap.data.remote.ApiFailure
import com.shunsoco.trainlivemap.data.remote.normalizeLinesQuery
import com.shunsoco.trainlivemap.domain.service.serviceStatusWithTrainDelayFallback
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import com.shunsoco.trainlivemap.domain.train.matchesStatusFilter
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val trainResponseLineQuery: String? = null,
    val serviceResponseLineQuery: String? = null,
    val communityReports: CommunityReportsApiResponse? = null,
    val communityLoading: Boolean = false,
    val communitySubmitting: Boolean = false,
    val communityError: String? = null,
    val communityMessage: String? = null,
    val communityRetryAtMillis: Long? = null,
) {
    val displayedLineIds: Set<String>
        get() {
            val preferred = if (preferences.favoritesOnly) {
                preferences.visibleLineIds intersect preferences.favoriteLineIds
            } else {
                preferences.visibleLineIds
            }
            val availableIds = railwayOptions
                .asSequence()
                .filter(RailwayFilterOption::available)
                .map(RailwayFilterOption::id)
                .toSet()
            return if (railwayOptions.isEmpty()) preferred else preferred intersect availableIds
        }

    val selectedLinesQuery: String?
        get() = if (preferences.visibleLineIdsInitialized) {
            normalizeLinesQuery(displayedLineIds)
        } else {
            null
        }

    /** Cached or failed snapshots stay available for diagnostics, never as current map positions. */
    val currentTrains: List<TrainLocation>
        get() = if (
            trainFromCache ||
            trainError != null ||
            trainResponseLineQuery == null ||
            trainResponseLineQuery != selectedLinesQuery
        ) {
            emptyList()
        } else {
            trains
        }

    val selectedTrain: TrainLocation?
        get() = currentTrains.firstOrNull { it.id == selectedTrainId }

    /**
     * Keep the API status untouched and derive only what is rendered. This
     * lets the estimate disappear as soon as its train snapshot becomes old.
     */
    val effectiveServiceStatuses: List<ServiceStatus>
        get() = if (serviceResponseLineQuery != selectedLinesQuery) {
            emptyList()
        } else serviceStatuses
            .ifEmpty { listOfNotNull(serviceStatus) }
            .map { officialStatus ->
            serviceStatusWithTrainDelayFallback(
                serviceStatus = officialStatus,
                trains = currentTrains,
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

            val visibleStatuses = selectedLinesQuery?.let {
                val lineIds = displayedLineIds
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
        get() = trainFromCache ||
            trainError != null ||
            dataUpdatedAt.isOlderThan(nowMillis, STALE_AFTER_MILLIS)

    val nextRefreshSeconds: Int?
        get() = nextTrainRefreshAtMillis?.let { refreshAt ->
            (((refreshAt - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
        }
}

class MainViewModel(
    private val repository: TrainRepository,
    private val settingsStore: SettingsStore,
    private val communityReportRepository: CommunityReportRepository? = null,
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

    private var pollingJob: Job? = null
    private var retryJob: Job? = null
    private var communityLoadJob: Job? = null
    private var communitySubmitJob: Job? = null
    private var trainRetryGate: PollingRetryGate? = null
    private var serviceRetryGate: PollingRetryGate? = null

    init {
        viewModelScope.launch(dispatcher) {
            settingsStore.preferences.collect { preferences ->
                var lineQueryChanged = false
                mutableState.update { previous ->
                    val updated = previous.copy(preferences = preferences)
                    lineQueryChanged = previous.selectedLinesQuery != updated.selectedLinesQuery
                    updated
                }
                if (lineQueryChanged && mutableState.value.isForeground) {
                    startPolling()
                }
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
        if (retryJob?.isActive == true) return
        retryJob = viewModelScope.launch(dispatcher) {
            kotlinx.coroutines.coroutineScope {
                launch { refreshTrainsNow(manual = true) }
                launch { refreshServiceStatusNow(manual = true) }
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

    fun selectTrainFromSearch(trainId: String) {
        mutableState.update {
            it.copy(
                selectedTrainId = trainId,
                statusFilter = TrainStatusFilter.ALL,
            )
        }
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

    /** Loads community summaries only after the user opens the sheet or retries. */
    fun loadCommunityReports() {
        val communityRepository = communityReportRepository ?: return
        if (communityLoadJob?.isActive == true) return
        val now = clockMillis()
        if ((mutableState.value.communityRetryAtMillis ?: 0L) > now) return
        communityLoadJob = viewModelScope.launch(dispatcher) {
            mutableState.update {
                it.copy(
                    communityLoading = true,
                    communityError = null,
                    communityMessage = null,
                    nowMillis = clockMillis(),
                )
            }
            val result = communityRepository.getReports()
            val completedAt = clockMillis()
            mutableState.update { previous ->
                previous.copy(
                    communityReports = result.data ?: previous.communityReports,
                    communityLoading = false,
                    communityError = result.apiFailure.communityErrorMessage(),
                    communityRetryAtMillis = result.apiFailure.communityRetryAt(completedAt),
                    nowMillis = completedAt,
                )
            }
        }
    }

    /** One user action causes exactly one POST; failures are never auto-retried. */
    fun submitCommunityReport(request: CommunityReportSubmitRequest) {
        val communityRepository = communityReportRepository ?: return
        if (communitySubmitJob?.isActive == true) return
        val now = clockMillis()
        if ((mutableState.value.communityRetryAtMillis ?: 0L) > now) return
        communitySubmitJob = viewModelScope.launch(dispatcher) {
            mutableState.update {
                it.copy(
                    communitySubmitting = true,
                    communityError = null,
                    communityMessage = null,
                    nowMillis = clockMillis(),
                )
            }
            val result = communityRepository.submitReport(request)
            val completedAt = clockMillis()
            mutableState.update { previous ->
                val response = result.data
                previous.copy(
                    communityReports = response?.let {
                        CommunityReportsApiResponse(
                            summaries = it.summaries,
                            windowMinutes = it.windowMinutes,
                            cooldownSeconds = it.cooldownSeconds,
                            persistent = it.persistent,
                            votingEnabled = it.votingEnabled,
                        )
                    } ?: previous.communityReports,
                    communitySubmitting = false,
                    communityError = result.apiFailure.communityErrorMessage(),
                    communityMessage = if (result.isSuccess) {
                        "運行状況の投稿を受け付けました"
                    } else {
                        null
                    },
                    communityRetryAtMillis = if (result.isSuccess && response != null) {
                        completedAt.plusDelaySafely(response.cooldownSeconds * 1_000L)
                    } else {
                        result.apiFailure.communityRetryAt(completedAt)
                    },
                    nowMillis = completedAt,
                )
            }
        }
    }

    private fun refreshRailways() {
        viewModelScope.launch(dispatcher) {
            refreshRailwaysNow()
        }
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                kotlinx.coroutines.coroutineScope {
                    launch { refreshTrainsNow() }
                    launch { refreshServiceStatusNow() }
                }
                val now = clockMillis()
                val baseline = minOf(trainPollingMillis, servicePollingMillis)
                val trainRetryAt = trainRetryGate?.retryAtMillis
                mutableState.update {
                    it.copy(
                        nowMillis = now,
                        nextTrainRefreshAtMillis = when (trainRetryAt) {
                            Long.MAX_VALUE -> null
                            null -> now + baseline
                            else -> maxOf(now + baseline, trainRetryAt)
                        },
                    )
                }
                delay(baseline)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        mutableState.update { it.copy(nextTrainRefreshAtMillis = null) }
    }

    internal suspend fun refreshTrainsNow(manual: Boolean = false) {
        trainRefreshMutex.withLock {
            val before = mutableState.value
            val lineQuery = before.selectedLinesQuery ?: return
            val now = clockMillis()
            if (!trainRetryGate.allowsRequest(lineQuery, now, manual)) {
                mutableState.update { it.copy(nowMillis = now) }
                return
            }
            val lineIds = before.displayedLineIds
            mutableState.update { it.copy(trainsLoading = it.currentTrains.isEmpty()) }
            val result = repository.refreshTrains(lineIds)
            trainRetryGate = nextPollingRetryGate(
                previous = trainRetryGate,
                lineQuery = lineQuery,
                apiFailure = result.apiFailure,
                nowMillis = now,
            )
            mutableState.update { previous ->
                val response = result.data
                val liveTrains = if (result.isSuccess && !result.fromCache) {
                    response?.trains.orEmpty()
                } else {
                    emptyList()
                }
                val nextFilter = previous.statusFilter.takeIf { filter ->
                    filter == TrainStatusFilter.ALL ||
                        liveTrains.any { train -> matchesStatusFilter(train, filter) }
                } ?: TrainStatusFilter.ALL
                previous.copy(
                    trains = response?.trains ?: previous.trains,
                    trainsLoading = false,
                    trainFromCache = result.fromCache,
                    trainError = result.userFacingError("列車情報"),
                    generatedAt = response?.generatedAt ?: previous.generatedAt,
                    dataUpdatedAt = response?.dataUpdatedAt ?: previous.dataUpdatedAt,
                    trainSource = response?.source ?: previous.trainSource,
                    isMock = response?.isMock ?: previous.isMock,
                    fallback = response?.fallback ?: previous.fallback,
                    notice = if (response != null) response.notice else previous.notice,
                    nowMillis = clockMillis(),
                    trainResponseLineQuery = if (response != null) {
                        lineQuery
                    } else {
                        previous.trainResponseLineQuery
                    },
                    statusFilter = nextFilter,
                    selectedTrainId = previous.selectedTrainId?.takeIf { selectedId ->
                        liveTrains.any { it.id == selectedId }
                    },
                )
            }
        }
    }

    internal suspend fun refreshServiceStatusNow(manual: Boolean = false) {
        serviceRefreshMutex.withLock {
            val before = mutableState.value
            val lineQuery = before.selectedLinesQuery ?: return
            val now = clockMillis()
            if (!serviceRetryGate.allowsRequest(lineQuery, now, manual)) {
                mutableState.update { it.copy(nowMillis = now) }
                return
            }
            mutableState.update {
                it.copy(serviceLoading = it.serviceStatus == null && it.serviceStatuses.isEmpty())
            }
            val result = repository.refreshServiceStatus(before.displayedLineIds)
            serviceRetryGate = nextPollingRetryGate(
                previous = serviceRetryGate,
                lineQuery = lineQuery,
                apiFailure = result.apiFailure,
                nowMillis = now,
            )
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
                    serviceError = result.userFacingError("運行情報"),
                    serviceSource = response?.source ?: previous.serviceSource,
                    serviceIsMock = response?.isMock ?: previous.serviceIsMock,
                    serviceFallback = response?.fallback ?: previous.serviceFallback,
                    serviceNotice = if (response != null) {
                        response.notice
                    } else {
                        previous.serviceNotice
                    },
                    serviceResponseLineQuery = if (response != null) {
                        lineQuery
                    } else {
                        previous.serviceResponseLineQuery
                    },
                    nowMillis = clockMillis(),
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
                    railwaysError = result.userFacingError("路線情報"),
                    railwaySource = response?.source ?: previous.railwaySource,
                    nowMillis = clockMillis(),
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
        const val TRAIN_POLLING_MILLIS = 10_000L
        const val SERVICE_POLLING_MILLIS = 10_000L
        private const val TOKAIDO_LINE_ID = "tokaido"
    }
}

class MainViewModelFactory(
    private val repository: TrainRepository,
    private val settingsStore: SettingsStore,
    private val communityReportRepository: CommunityReportRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(
            repository = repository,
            settingsStore = settingsStore,
            communityReportRepository = communityReportRepository,
        ) as T
    }
}

private fun LoadResult<*>.userFacingError(endpointName: String): String? = when {
    error == null -> null
    apiFailure is ApiFailure.BadRequest ->
        "${endpointName}の路線指定を確認してください。選択変更まで自動再試行を停止します。"
    apiFailure is ApiFailure.RateLimited ->
        "${endpointName}へのアクセスが集中しています。指定された時間まで待って再試行します。"
    apiFailure is ApiFailure.ServiceUnavailable ->
        "${endpointName}のAPIが一時的に利用できません。間隔を空けて再試行します。"
    apiFailure is ApiFailure.Network ->
        "オフラインです。${endpointName}の保存済み情報があっても現在位置としては表示しません。"
    else -> "${endpointName}の更新に失敗しました。"
}

private fun ApiFailure?.communityErrorMessage(): String? = when (this) {
    null -> null
    is ApiFailure.BadRequest -> "投稿内容を確認してください。"
    is ApiFailure.RateLimited -> "連続投稿を避けるため、指定された時間までお待ちください。"
    is ApiFailure.ServiceUnavailable -> "投稿機能は一時的に利用できません。後でもう一度お試しください。"
    is ApiFailure.Network -> "オフラインのため投稿できません。"
    is ApiFailure.Http -> "投稿の取得または送信に失敗しました（HTTP $statusCode）。"
    is ApiFailure.Unexpected -> "投稿の取得または送信に失敗しました。"
}

private fun ApiFailure?.communityRetryAt(nowMillis: Long): Long? =
    (this as? ApiFailure.RateLimited)?.let { failure ->
        val delayMillis = failure.retryAfterMillis ?: DEFAULT_COMMUNITY_COOLDOWN_MILLIS
        if (delayMillis > 0L && nowMillis > Long.MAX_VALUE - delayMillis) {
            Long.MAX_VALUE
        } else {
            nowMillis + delayMillis
        }
    }

private fun Long.plusDelaySafely(delayMillis: Long): Long =
    if (delayMillis > 0L && this > Long.MAX_VALUE - delayMillis) Long.MAX_VALUE else this + delayMillis

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
private const val DEFAULT_COMMUNITY_COOLDOWN_MILLIS = 60_000L

private val ServiceSeverity.displayPriority: Int
    get() = when (this) {
        ServiceSeverity.NORMAL -> 0
        ServiceSeverity.MINOR -> 1
        ServiceSeverity.MAJOR -> 2
    }
