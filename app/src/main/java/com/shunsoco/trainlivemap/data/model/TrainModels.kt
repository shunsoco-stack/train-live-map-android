package com.shunsoco.trainlivemap.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrainDirection {
    @SerialName("inbound")
    INBOUND,

    @SerialName("outbound")
    OUTBOUND,
}

@Serializable
enum class TrainStatus {
    @SerialName("running")
    RUNNING,

    @SerialName("stopped")
    STOPPED,

    @SerialName("delayed")
    DELAYED,

    @SerialName("suspended")
    SUSPENDED,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
enum class DataAccuracy {
    @SerialName("actual")
    ACTUAL,

    @SerialName("estimated")
    ESTIMATED,

    @SerialName("mock")
    MOCK,
}

@Serializable
enum class TrainType {
    @SerialName("local")
    LOCAL,

    @SerialName("rapid")
    RAPID,

    @SerialName("special_rapid")
    SPECIAL_RAPID,
}

@Serializable
enum class ProviderSource {
    @SerialName("odpt")
    ODPT,

    @SerialName("mock")
    MOCK,
}

@Serializable
data class RouteSegmentEstimate(
    val fromFraction: Double,
    val toFraction: Double,
    val coordinates: List<LngLat>? = null,
)

@Serializable
data class TrainLocation(
    val id: String,
    val lineId: String,
    val lineName: String,
    val lineColor: String,
    /**
     * Kept only because it is part of the backend contract.
     * Product UI and accessibility descriptions intentionally do not expose it.
     */
    val trainNumber: String,
    val direction: TrainDirection,
    val destination: String,
    val trainType: TrainType,
    val latitude: Double,
    val longitude: Double,
    val delayMinutes: Int,
    val speedKmh: Double,
    val status: TrainStatus,
    val lastUpdatedAt: String,
    val stoppedSince: String?,
    val dataAccuracy: DataAccuracy,
    val routeSegment: RouteSegmentEstimate?,
)

@Serializable
data class ServiceStatus(
    val lineName: String,
    val severity: ServiceSeverity,
    val message: String,
    val updatedAt: String,
    val dataAccuracy: DataAccuracy,
)

@Serializable
enum class ServiceSeverity {
    @SerialName("normal")
    NORMAL,

    @SerialName("minor")
    MINOR,

    @SerialName("major")
    MAJOR,
}

@Serializable
data class TrainsApiResponse(
    val trains: List<TrainLocation>,
    val generatedAt: String,
    val dataUpdatedAt: String,
    val isMock: Boolean,
    val source: ProviderSource,
    val fallback: Boolean,
    val notice: String?,
)

@Serializable
data class ServiceStatusApiResponse(
    val serviceStatus: ServiceStatus,
    val isMock: Boolean,
    val source: ProviderSource,
    val fallback: Boolean,
    val notice: String?,
)
