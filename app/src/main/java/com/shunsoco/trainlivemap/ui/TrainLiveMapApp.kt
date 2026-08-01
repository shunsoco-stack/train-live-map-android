package com.shunsoco.trainlivemap.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shunsoco.trainlivemap.ads.AdsManager
import com.shunsoco.trainlivemap.ads.AnchoredAdaptiveBanner
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import com.shunsoco.trainlivemap.domain.train.matchesStatusFilter
import com.shunsoco.trainlivemap.ui.components.AppHeader
import com.shunsoco.trainlivemap.ui.components.CommunityReportsButton
import com.shunsoco.trainlivemap.ui.components.CurrentLocationButton
import com.shunsoco.trainlivemap.ui.components.DataHealthNotice
import com.shunsoco.trainlivemap.ui.components.LegendButton
import com.shunsoco.trainlivemap.ui.components.MapEmptyState
import com.shunsoco.trainlivemap.ui.components.RailwayFloatingButton
import com.shunsoco.trainlivemap.ui.components.ServiceStatusPanel
import com.shunsoco.trainlivemap.ui.components.TrainSearchOverlay
import com.shunsoco.trainlivemap.ui.components.TrainStatusFilterBar
import com.shunsoco.trainlivemap.ui.components.resolveMapEmptyState
import com.shunsoco.trainlivemap.ui.location.AndroidCurrentLocationProvider
import com.shunsoco.trainlivemap.ui.map.MapCameraRequest
import com.shunsoco.trainlivemap.ui.map.TrainMap
import com.shunsoco.trainlivemap.ui.sheets.CommunityReportsSheet
import com.shunsoco.trainlivemap.ui.sheets.LegendSheet
import com.shunsoco.trainlivemap.ui.sheets.RailwayFilterSheet
import com.shunsoco.trainlivemap.ui.sheets.TrainDetailSheet
import com.shunsoco.trainlivemap.ui.sheets.formatTimestamp
import com.shunsoco.trainlivemap.ui.theme.RailBrown
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrainLiveMapApp(
    state: MainUiState,
    adsManager: AdsManager,
    activity: Activity,
    onRetry: () -> Unit,
    onStatusFilterChanged: (TrainStatusFilter) -> Unit,
    onTrainSelected: (String?) -> Unit,
    onTrainSelectedFromSearch: (String) -> Unit,
    onVisibleChanged: (String, Boolean) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onVisibleLinesChanged: (Set<String>) -> Unit,
    onCommunityReportsRequested: () -> Unit,
    onCommunityReportSubmitted: (CommunityReportSubmitRequest) -> Unit,
) {
    var showRailwaySheet by remember { mutableStateOf(false) }
    var showLegendSheet by remember { mutableStateOf(false) }
    var showCommunitySheet by remember { mutableStateOf(false) }
    var locationLoading by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var cameraRequest by remember { mutableStateOf<MapCameraRequest?>(null) }
    val privacyRequirement by adsManager.privacyOptionsRequirement.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(activity) {
        AndroidCurrentLocationProvider(activity.applicationContext)
    }
    val moveToCurrentLocation: () -> Unit = {
        if (!locationLoading) {
            locationLoading = true
            locationMessage = null
            scope.launch {
                val coordinate = locationProvider.getCurrentLocation()
                if (coordinate == null) {
                    locationMessage = "現在地を取得できませんでした。端末の位置情報設定を確認してください。"
                } else {
                    cameraRequest = MapCameraRequest(
                        coordinate = coordinate,
                        zoom = CURRENT_LOCATION_ZOOM,
                        requestId = SystemClock.elapsedRealtime(),
                    )
                }
                locationLoading = false
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            moveToCurrentLocation()
        } else {
            locationMessage = "現在地へ移動するには、おおよその位置情報を許可してください。"
        }
    }

    val effectiveVisibleLineIds = state.displayedLineIds
    val trainsOnVisibleLines = remember(state.currentTrains, effectiveVisibleLineIds) {
        state.currentTrains.filter { it.lineId in effectiveVisibleLineIds }
    }
    val statusCounts = remember(trainsOnVisibleLines) {
        TrainStatusFilter.entries.associateWith { filter ->
            trainsOnVisibleLines.count { matchesStatusFilter(it, filter) }
        }
    }
    val filteredTrains = remember(trainsOnVisibleLines, state.statusFilter) {
        trainsOnVisibleLines.filter { matchesStatusFilter(it, state.statusFilter) }
    }
    val mapTrains = remember(filteredTrains, state.selectedTrain) {
        val selected = state.selectedTrain
        if (selected != null && filteredTrains.none { it.id == selected.id }) {
            filteredTrains + selected
        } else {
            filteredTrains
        }
    }
    val emptyState = resolveMapEmptyState(
        loading = state.trainsLoading ||
            (!state.preferences.visibleLineIdsInitialized && state.railwaysLoading),
        visibleLineCount = effectiveVisibleLineIds.size,
        trainsOnVisibleLinesCount = trainsOnVisibleLines.size,
        filteredTrainCount = filteredTrains.size,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                AnchoredAdaptiveBanner(adsManager = adsManager)
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            TrainMap(
                trains = mapTrains,
                railwayLines = state.railwayLines,
                visibleLineIds = effectiveVisibleLineIds,
                selectedTrainId = state.selectedTrainId,
                cameraRequest = cameraRequest,
                animationsActive = state.isForeground &&
                    !state.trainFromCache &&
                    state.trainError == null,
                onTrainSelected = onTrainSelected,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(RailBrown.copy(alpha = 0.97f)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 760.dp)
                    .statusBarsPadding(),
            ) {
                AppHeader(
                    state = state,
                    visibleTrainCount = trainsOnVisibleLines.size,
                    privacyRequirement = privacyRequirement,
                    onRefresh = onRetry,
                    onPrivacyOptions = { adsManager.showPrivacyOptionsForm(activity) },
                )
                if (emptyState == null) {
                    DataHealthNotice(state = state, onRetry = onRetry)
                }
                ServiceStatusPanel(
                    serviceStatus = state.effectiveServiceStatus,
                    fromCache = state.serviceFromCache,
                    isMock = state.serviceIsMock,
                    fallback = state.serviceFallback,
                )
                TrainStatusFilterBar(
                    selected = state.statusFilter,
                    counts = statusCounts,
                    onSelected = onStatusFilterChanged,
                )
                TrainSearchOverlay(
                    trains = trainsOnVisibleLines,
                    selectedTrainId = state.selectedTrainId,
                    onTrainSelected = onTrainSelectedFromSearch,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .align(Alignment.Start),
                )
            }

            emptyState?.let { kind ->
                MapEmptyState(
                    kind = kind,
                    supportingMessage = emptyStateSupportingMessage(state),
                    onChooseLines = { showRailwaySheet = true },
                    onResetFilter = { onStatusFilterChanged(TrainStatusFilter.ALL) },
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                locationMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrentLocationButton(
                        loading = locationLoading,
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(
                                    activity,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                moveToCurrentLocation()
                            } else {
                                locationPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            }
                        },
                    )
                    LegendButton(onClick = { showLegendSheet = true })
                    CommunityReportsButton(
                        onClick = {
                            showCommunitySheet = true
                            onCommunityReportsRequested()
                        },
                    )
                }
            }

            RailwayFloatingButton(
                selectedCount = effectiveVisibleLineIds.size,
                favoritesOnly = state.preferences.favoritesOnly,
                onClick = { showRailwaySheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 76.dp),
            )
        }
    }

    if (showRailwaySheet) {
        RailwayFilterSheet(
            options = state.railwayOptions,
            preferences = state.preferences,
            loading = state.railwaysLoading,
            onDismiss = { showRailwaySheet = false },
            onVisibleChanged = onVisibleChanged,
            onFavoriteChanged = onFavoriteChanged,
            onFavoritesOnlyChanged = onFavoritesOnlyChanged,
            onVisibleLinesChanged = onVisibleLinesChanged,
        )
    }

    if (showLegendSheet) {
        LegendSheet(
            onDismiss = { showLegendSheet = false },
            onOpenPrivacyPolicy = { uriHandler.openUri(PRIVACY_POLICY_URL) },
            onOpenTerms = { uriHandler.openUri(TERMS_URL) },
        )
    }

    if (showCommunitySheet) {
        CommunityReportsSheetHost(
            state = state,
            onRefresh = onCommunityReportsRequested,
            onSubmit = onCommunityReportSubmitted,
            onDismiss = { showCommunitySheet = false },
        )
    }

    state.selectedTrain?.let { selectedTrain ->
        TrainDetailSheet(
            train = selectedTrain,
            nowMillis = state.nowMillis,
            onDismiss = { onTrainSelected(null) },
        )
    }
}

@Composable
private fun CommunityReportsSheetHost(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSubmit: (CommunityReportSubmitRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val retryRemainingSeconds = retryRemainingSeconds(state.communityRetryAtMillis)
    CommunityReportsSheet(
        options = state.railwayOptions,
        reports = state.communityReports,
        loading = state.communityLoading,
        submitting = state.communitySubmitting,
        error = state.communityError,
        message = state.communityMessage,
        retryRemainingSeconds = retryRemainingSeconds,
        onRefresh = onRefresh,
        onSubmit = onSubmit,
        onDismiss = onDismiss,
    )
}

@Composable
private fun retryRemainingSeconds(retryAtMillis: Long?): Int? {
    if (retryAtMillis == null) return null
    var nowMillis by remember(retryAtMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(retryAtMillis) {
        while (nowMillis < retryAtMillis) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    return ceil((retryAtMillis - nowMillis).coerceAtLeast(0L) / 1_000.0).toInt()
}

private fun emptyStateSupportingMessage(state: MainUiState): String? {
    val messages = buildList {
        if (state.isStale) add("データが古い状態です。")
        if (state.isMock) add("列車位置はモックデータです。")
        if (state.fallback) add("列車位置はフォールバックデータです。")
        state.notice?.takeIf(String::isNotBlank)?.let(::add)
        state.trainError?.takeIf(String::isNotBlank)?.let(::add)
        state.serviceError?.takeIf(String::isNotBlank)?.let(::add)
        state.railwaysError?.takeIf(String::isNotBlank)?.let(::add)
        if (state.trainFromCache || state.trainError != null) {
            add("以前に取得した列車位置は、現在位置として表示していません。")
        }
        state.dataUpdatedAt?.let { add("最終更新 ${formatTimestamp(it)}") }
    }.distinct()
    return messages.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
}

private const val CURRENT_LOCATION_ZOOM = 13.0
private const val PRIVACY_POLICY_URL = "https://train-live-map.vercel.app/privacy"
private const val TERMS_URL = "https://train-live-map.vercel.app/terms"
