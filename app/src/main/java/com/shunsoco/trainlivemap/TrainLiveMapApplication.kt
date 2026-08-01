package com.shunsoco.trainlivemap

import android.app.Application
import com.shunsoco.trainlivemap.data.local.AppDataStore
import com.shunsoco.trainlivemap.data.local.CommunityReporterIdDataStore
import com.shunsoco.trainlivemap.data.local.SettingsStore
import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import com.shunsoco.trainlivemap.data.remote.RetrofitCommunityReportRemoteDataSource
import com.shunsoco.trainlivemap.data.repository.CommunityReportRepository
import com.shunsoco.trainlivemap.data.repository.DefaultCommunityReportRepository
import com.shunsoco.trainlivemap.data.repository.DefaultTrainRepository
import com.shunsoco.trainlivemap.data.repository.TrainRepository
import org.maplibre.android.MapLibre

class TrainLiveMapApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val json = ApiClientFactory.json
    private val api = ApiClientFactory.create(BuildConfig.API_BASE_URL, json)
    private val store = AppDataStore.create(application.applicationContext)
    private val voterIdStore = CommunityReporterIdDataStore.create(application.applicationContext)

    val repository: TrainRepository = DefaultTrainRepository(api, store, json)
    val communityReportRepository: CommunityReportRepository = DefaultCommunityReportRepository(
        remoteDataSource = RetrofitCommunityReportRemoteDataSource(api),
        voterIdStore = voterIdStore,
    )
    val settingsStore: SettingsStore = store
}
