package com.shunsoco.trainlivemap.ui.map

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shunsoco.trainlivemap.data.model.LngLat
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.ui.components.TrainMarker
import java.util.Locale
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

private data class ProjectedTrain(
    val train: TrainLocation,
    val x: Float,
    val y: Float,
)

private class MapHolder {
    var map by mutableStateOf<MapLibreMap?>(null)
    var style by mutableStateOf<Style?>(null)
    var activeMapView: MapView? = null

    fun activate(mapView: MapView) {
        activeMapView = mapView
        map = null
        style = null
    }

    fun attach(
        mapView: MapView,
        mapLibreMap: MapLibreMap,
        loadedStyle: Style,
    ) {
        if (activeMapView !== mapView) return
        map = mapLibreMap
        style = loadedStyle
    }

    fun detach(mapView: MapView) {
        if (activeMapView !== mapView) return
        activeMapView = null
        map = null
        style = null
    }
}

private class MapViewLifecycleController(
    private val mapView: MapView,
) {
    private var started = false
    private var resumed = false
    var isDestroyed: Boolean = false
        private set

    fun onEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    private fun start() {
        if (isDestroyed || started) return
        mapView.onStart()
        started = true
    }

    private fun resume() {
        if (isDestroyed || resumed) return
        start()
        mapView.onResume()
        resumed = true
    }

    private fun pause() {
        if (isDestroyed || !resumed) return
        mapView.onPause()
        resumed = false
    }

    private fun stop() {
        if (isDestroyed || !started) return
        pause()
        mapView.onStop()
        started = false
    }

    fun destroy() {
        if (isDestroyed) return
        pause()
        stop()
        mapView.onDestroy()
        isDestroyed = true
    }
}

private data class ManagedMapView(
    val view: MapView,
    val lifecycleController: MapViewLifecycleController,
)

@Composable
fun TrainMap(
    trains: List<TrainLocation>,
    railwayLines: List<RailwayMapLine>,
    visibleLineIds: Set<String>,
    selectedTrainId: String?,
    animationsActive: Boolean,
    onTrainSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = remember { MapHolder() }
    val motion = remember { TrainMotionCoordinator() }
    var viewportWidth by remember { mutableStateOf(0) }
    var viewportHeight by remember { mutableStateOf(0) }
    var projectedTrains by remember { mutableStateOf(emptyList<ProjectedTrain>()) }
    val trainsById = remember(trains) { trains.associateBy(TrainLocation::id) }
    val density = LocalDensity.current
    val markerCenterX = with(density) { 36.dp.toPx() }
    val markerCenterY = with(density) { 43.dp.toPx() }
    val markerMargin = with(density) { 100.dp.toPx() }
    val attributionMargin = with(density) { 8.dp.roundToPx() }
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)

    val mapView = rememberMapView(holder)

    LaunchedEffect(holder.map, attributionMargin, navigationBarBottom) {
        holder.map?.uiSettings?.apply {
            setLogoMargins(
                logoMarginLeft,
                logoMarginTop,
                logoMarginRight,
                attributionMargin + navigationBarBottom,
            )
            setAttributionMargins(
                attributionMarginLeft,
                attributionMarginTop,
                attributionMarginRight,
                attributionMargin + navigationBarBottom,
            )
        }
    }

    LaunchedEffect(holder.style, railwayLines, visibleLineIds) {
        holder.style?.let { style ->
            updateRailwayLayers(
                style = style,
                lines = railwayLines,
                visibleLineIds = visibleLineIds,
            )
        }
    }

    LaunchedEffect(trains, railwayLines) {
        motion.updateTargets(
            trains = trains,
            railwayLines = railwayLines,
            nowMillis = SystemClock.elapsedRealtime(),
        )
    }

    LaunchedEffect(
        holder.map,
        animationsActive,
        trainsById,
        viewportWidth,
        viewportHeight,
        markerMargin,
    ) {
        if (!animationsActive) return@LaunchedEffect
        while (true) {
            val map = holder.map
            if (map != null && viewportWidth > 0 && viewportHeight > 0) {
                val now = SystemClock.elapsedRealtime()
                val next = ArrayList<ProjectedTrain>()
                for ((trainId, coordinate) in motion.positions(now)) {
                    val train = trainsById[trainId] ?: continue
                    val point = map.projection.toScreenLocation(
                        LatLng(coordinate.latitude, coordinate.longitude),
                    )
                    if (
                        point.x >= -markerMargin &&
                        point.y >= -markerMargin &&
                        point.x <= viewportWidth + markerMargin &&
                        point.y <= viewportHeight + markerMargin
                    ) {
                        next += ProjectedTrain(train, point.x, point.y)
                    }
                }
                projectedTrains = next
                delay(if (motion.isAnimating(now)) 33L else 120L)
            } else {
                delay(100L)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                viewportWidth = it.width
                viewportHeight = it.height
            },
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        for (projected in projectedTrains) {
            TrainMarker(
                train = projected.train,
                selected = projected.train.id == selectedTrainId,
                onClick = { onTrainSelected(projected.train.id) },
                modifier = Modifier.offset {
                    IntOffset(
                        x = (projected.x - markerCenterX).toInt(),
                        y = (projected.y - markerCenterY).toInt(),
                    )
                },
            )
        }
    }
}

@Composable
private fun rememberMapView(holder: MapHolder): MapView {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val managedMapView = remember(context, lifecycleOwner) {
        val mapOptions = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
        val mapView = MapView(context, mapOptions)
        val lifecycleController = MapViewLifecycleController(mapView)
        holder.activate(mapView)
        mapView.onCreate(Bundle())
        mapView.getMapAsync mapReady@{ mapLibreMap ->
            if (lifecycleController.isDestroyed || holder.activeMapView !== mapView) {
                return@mapReady
            }
            mapLibreMap.uiSettings.isCompassEnabled = false
            mapLibreMap.uiSettings.setAttributionTintColor(
                android.graphics.Color.rgb(43, 29, 23),
            )
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(35.68, 139.70))
                .zoom(8.15)
                .build()
            mapLibreMap.setStyle(
                Style.Builder().fromJson(BASE_MAP_STYLE_JSON),
            ) { loadedStyle ->
                if (!lifecycleController.isDestroyed) {
                    holder.attach(mapView, mapLibreMap, loadedStyle)
                }
            }
        }
        ManagedMapView(mapView, lifecycleController)
    }

    DisposableEffect(lifecycleOwner, managedMapView) {
        val observer = LifecycleEventObserver { _, event ->
            managedMapView.lifecycleController.onEvent(event)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            managedMapView.lifecycleController.destroy()
            holder.detach(managedMapView.view)
        }
    }
    return managedMapView.view
}

private fun updateRailwayLayers(
    style: Style,
    lines: List<RailwayMapLine>,
    visibleLineIds: Set<String>,
) {
    val activeLayerIds = mutableSetOf<String>()
    for (line in lines) {
        val safeId = line.id.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "-")
        val paths = line.coordinates.filter { it.size >= 2 }
        if (paths.isEmpty()) continue
        activeLayerIds += safeId
        val sourceId = "railway-source-$safeId"
        val casingId = "railway-casing-$safeId"
        val lineLayerId = "railway-line-$safeId"
        val geometry = MultiLineString.fromLngLats(
            paths.map { path ->
                path.map { coordinate ->
                    Point.fromLngLat(coordinate.longitude, coordinate.latitude)
                }
            },
        )
        val feature = Feature.fromGeometry(geometry)
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, feature))
            style.addLayer(
                LineLayer(casingId, sourceId).withProperties(
                    lineColor("#2B1D17"),
                    lineWidth(7f),
                    lineOpacity(0.88f),
                    visibility(
                        if (line.id in visibleLineIds) Property.VISIBLE else Property.NONE,
                    ),
                ),
            )
            style.addLayer(
                LineLayer(lineLayerId, sourceId).withProperties(
                    lineColor(line.color),
                    lineWidth(4f),
                    lineOpacity(0.95f),
                    visibility(
                        if (line.id in visibleLineIds) Property.VISIBLE else Property.NONE,
                    ),
                ),
            )
        } else {
            source.setGeoJson(feature)
            val value = if (line.id in visibleLineIds) Property.VISIBLE else Property.NONE
            style.getLayer(casingId)?.setProperties(visibility(value))
            style.getLayer(lineLayerId)?.setProperties(
                visibility(value),
                lineColor(line.color),
            )
        }
    }

    // Any layer that disappeared from the latest API snapshot is hidden.
    style.layers
        .filter { it.id.startsWith("railway-line-") || it.id.startsWith("railway-casing-") }
        .filter { layer ->
            activeLayerIds.none { safeId -> layer.id.endsWith(safeId) }
        }
        .forEach { it.setProperties(visibility(Property.NONE)) }
}

private val BASE_MAP_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "osm-voyager": {
      "type": "raster",
      "tiles": [
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors, © CARTO"
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#e8eaed" }
    },
    {
      "id": "osm-voyager",
      "type": "raster",
      "source": "osm-voyager"
    }
  ]
}
""".trimIndent()
