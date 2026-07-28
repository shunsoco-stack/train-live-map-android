package com.shunsoco.trainlivemap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RailwayCoverage {
    @SerialName("realtime")
    REALTIME,

    @SerialName("limited")
    LIMITED,

    @SerialName("unavailable")
    UNAVAILABLE,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
enum class RailwayKind {
    @SerialName("line")
    LINE,

    @SerialName("service")
    SERVICE,
}

@Serializable
data class RailwayCatalogLine(
    val id: String,
    val name: String,
    val category: String,
    val color: String,
    val aliases: List<String>,
    val coverage: RailwayCoverage,
    val coverageNote: String?,
    val kind: RailwayKind,
)

@Serializable
data class RailwayMapLine(
    val id: String,
    val odptId: String,
    val name: String,
    val color: String,
    val coordinates: List<List<LngLat>>,
)

@Serializable
data class RailwayFilterOption(
    val id: String,
    val name: String,
    val category: String,
    val color: String,
    val aliases: List<String>,
    val coverage: RailwayCoverage,
    val coverageNote: String?,
    val kind: RailwayKind,
    val available: Boolean,
)

@Serializable
enum class RailwaySource {
    @SerialName("odpt")
    ODPT,

    @SerialName("fallback")
    FALLBACK,
}

@Serializable
data class RailwaysApiResponse(
    val lines: List<RailwayMapLine>,
    val options: List<RailwayFilterOption>,
    val generatedAt: String,
    val source: RailwaySource,
)
