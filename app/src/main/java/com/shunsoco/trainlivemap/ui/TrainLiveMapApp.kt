package com.shunsoco.trainlivemap.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shunsoco.trainlivemap.ads.AdsManager
import com.shunsoco.trainlivemap.ads.AnchoredAdaptiveBanner
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import com.shunsoco.trainlivemap.domain.train.matchesStatusFilter
import com.shunsoco.trainlivemap.ui.components.AppHeader
import com.shunsoco.trainlivemap.ui.components.DataHealthNotice
import com.shunsoco.trainlivemap.ui.components.RailwayFloatingButton
import com.shunsoco.trainlivemap.ui.components.ServiceStatusPanel
import com.shunsoco.trainlivemap.ui.components.TrainStatusFilterBar
import com.shunsoco.trainlivemap.ui.map.TrainMap
import com.shunsoco.trainlivemap.ui.sheets.RailwayFilterSheet
import com.shunsoco.trainlivemap.ui.sheets.TrainDetailSheet
import com.shunsoco.trainlivemap.ui.theme.RailBrown

@Composable
fun TrainLiveMapApp(
    state: MainUiState,
    adsManager: AdsManager,
    activity: Activity,
    onRetry: () -> Unit,
    onStatusFilterChanged: (TrainStatusFilter) -> Unit,
    onTrainSelected: (String?) -> Unit,
    onVisibleChanged: (String, Boolean) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onVisibleLinesChanged: (Set<String>) -> Unit,
) {
    var showRailwaySheet by remember { mutableStateOf(false) }
    val privacyRequirement by adsManager.privacyOptionsRequirement.collectAsStateWithLifecycle()
    val effectiveVisibleLineIds = remember(state.preferences) {
        if (state.preferences.favoritesOnly) {
            state.preferences.visibleLineIds intersect state.preferences.favoriteLineIds
        } else {
            state.preferences.visibleLineIds
        }
    }
    val trainsOnVisibleLines = remember(state.trains, effectiveVisibleLineIds) {
        state.trains.filter { it.lineId in effectiveVisibleLineIds }
    }
    val statusCounts = remember(trainsOnVisibleLines) {
        TrainStatusFilter.entries.associateWith { filter ->
            trainsOnVisibleLines.count { matchesStatusFilter(it, filter) }
        }
    }
    val visibleTrains = remember(trainsOnVisibleLines, state.statusFilter) {
        trainsOnVisibleLines.filter { matchesStatusFilter(it, state.statusFilter) }
    }

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
                AnchoredAdaptiveBanner(
                    adsManager = adsManager,
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            TrainMap(
                trains = visibleTrains,
                railwayLines = state.railwayLines,
                visibleLineIds = effectiveVisibleLineIds,
                selectedTrainId = state.selectedTrainId,
                animationsActive = state.isForeground,
                onTrainSelected = { onTrainSelected(it) },
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
                    visibleTrainCount = visibleTrains.size,
                    privacyRequirement = privacyRequirement,
                    onRefresh = onRetry,
                    onPrivacyOptions = { adsManager.showPrivacyOptionsForm(activity) },
                )
                DataHealthNotice(state = state, onRetry = onRetry)
                ServiceStatusPanel(
                    serviceStatus = state.serviceStatus,
                    fromCache = state.serviceFromCache,
                    isMock = state.serviceIsMock,
                    fallback = state.serviceFallback,
                )
                TrainStatusFilterBar(
                    selected = state.statusFilter,
                    counts = statusCounts,
                    onSelected = onStatusFilterChanged,
                )
            }

            RailwayFloatingButton(
                selectedCount = effectiveVisibleLineIds.size,
                favoritesOnly = state.preferences.favoritesOnly,
                onClick = { showRailwaySheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 58.dp),
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

    state.selectedTrain?.let { selectedTrain ->
        TrainDetailSheet(
            train = selectedTrain,
            nowMillis = state.nowMillis,
            onDismiss = { onTrainSelected(null) },
        )
    }
}
