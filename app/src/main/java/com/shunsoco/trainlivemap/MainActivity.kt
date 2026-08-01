package com.shunsoco.trainlivemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shunsoco.trainlivemap.ads.AdsManager
import com.shunsoco.trainlivemap.ui.MainViewModel
import com.shunsoco.trainlivemap.ui.MainViewModelFactory
import com.shunsoco.trainlivemap.ui.TrainLiveMapApp
import com.shunsoco.trainlivemap.ui.theme.TrainLiveMapTheme

class MainActivity : ComponentActivity() {
    private lateinit var adsManager: AdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        adsManager = AdsManager.getInstance(applicationContext)
        adsManager.requestConsent(this)

        val container = (application as TrainLiveMapApplication).container
        val factory = MainViewModelFactory(
            repository = container.repository,
            settingsStore = container.settingsStore,
            communityReportRepository = container.communityReportRepository,
        )

        setContent {
            val mainViewModel: MainViewModel = viewModel(factory = factory)
            val state by mainViewModel.state.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, mainViewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> mainViewModel.setForeground(true)
                        Lifecycle.Event.ON_STOP -> mainViewModel.setForeground(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    mainViewModel.setForeground(true)
                }
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    mainViewModel.setForeground(false)
                }
            }

            TrainLiveMapTheme {
                TrainLiveMapApp(
                    state = state,
                    adsManager = adsManager,
                    activity = this,
                    onRetry = mainViewModel::retryAll,
                    onStatusFilterChanged = mainViewModel::setStatusFilter,
                    onTrainSelected = mainViewModel::selectTrain,
                    onTrainSelectedFromSearch = mainViewModel::selectTrainFromSearch,
                    onVisibleChanged = mainViewModel::setVisible,
                    onFavoriteChanged = mainViewModel::setFavorite,
                    onFavoritesOnlyChanged = mainViewModel::setFavoritesOnly,
                    onVisibleLinesChanged = mainViewModel::setVisibleLines,
                    onCommunityReportsRequested = mainViewModel::loadCommunityReports,
                    onCommunityReportSubmitted = mainViewModel::submitCommunityReport,
                )
            }
        }
    }
}
