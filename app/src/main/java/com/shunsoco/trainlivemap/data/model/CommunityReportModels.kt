package com.shunsoco.trainlivemap.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class CommunityReportStatus {
    @SerialName("on-time")
    ON_TIME,

    @SerialName("delayed")
    DELAYED,

    @SerialName("suspended")
    SUSPENDED,
}

@Serializable
data class CommunityReportCounts(
    val onTime: Int,
    val delayed: Int,
    val suspended: Int,
)

@Serializable
data class CommunityReportSummary(
    val lineId: String,
    val status: CommunityReportStatus,
    val delayMinutes: Int?,
    val voteCount: Int,
    val counts: CommunityReportCounts,
    val updatedAt: String,
)

@Serializable
data class CommunityReportsApiResponse(
    val summaries: List<CommunityReportSummary>,
    val windowMinutes: Int,
    val cooldownSeconds: Int,
    val persistent: Boolean,
    val votingEnabled: Boolean,
)

/**
 * Body accepted by `POST /api/community-reports`.
 *
 * Delayed reports require an integral 1..120 minute value. The other two
 * statuses intentionally send JSON null, matching the Web API validator.
 */
@Serializable(with = CommunityReportSubmitRequestSerializer::class)
data class CommunityReportSubmitRequest(
    val lineId: String,
    val status: CommunityReportStatus,
    val delayMinutes: Int?,
) {
    init {
        require(lineId.isNotBlank() && lineId == lineId.trim()) {
            "lineId must be non-blank and trimmed"
        }
        if (status == CommunityReportStatus.DELAYED) {
            require(delayMinutes != null && delayMinutes in 1..120) {
                "Delayed reports require delayMinutes in 1..120"
            }
        } else {
            require(delayMinutes == null) {
                "On-time and suspended reports require null delayMinutes"
            }
        }
    }
}

/** Always emits `delayMinutes`, including JSON null for non-delayed votes. */
object CommunityReportSubmitRequestSerializer : KSerializer<CommunityReportSubmitRequest> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "CommunityReportSubmitRequest",
        kind = PrimitiveKind.STRING,
    )

    override fun serialize(
        encoder: Encoder,
        value: CommunityReportSubmitRequest,
    ) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("Community report requests require JSON")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("lineId", JsonPrimitive(value.lineId))
                put(
                    "status",
                    JsonPrimitive(
                        when (value.status) {
                            CommunityReportStatus.ON_TIME -> "on-time"
                            CommunityReportStatus.DELAYED -> "delayed"
                            CommunityReportStatus.SUSPENDED -> "suspended"
                        },
                    ),
                )
                put(
                    "delayMinutes",
                    value.delayMinutes?.let(::JsonPrimitive) ?: JsonNull,
                )
            },
        )
    }

    override fun deserialize(decoder: Decoder): CommunityReportSubmitRequest {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("Community report requests require JSON")
        val value = jsonDecoder.decodeJsonElement().jsonObject
        val lineId = value["lineId"]?.jsonPrimitive?.contentOrNull
            ?: throw SerializationException("Missing lineId")
        val status = when (value["status"]?.jsonPrimitive?.contentOrNull) {
            "on-time" -> CommunityReportStatus.ON_TIME
            "delayed" -> CommunityReportStatus.DELAYED
            "suspended" -> CommunityReportStatus.SUSPENDED
            else -> throw SerializationException("Invalid community report status")
        }
        return CommunityReportSubmitRequest(
            lineId = lineId,
            status = status,
            delayMinutes = value["delayMinutes"]?.jsonPrimitive?.intOrNull,
        )
    }
}

@Serializable
data class CommunityReportSubmitResponse(
    val summaries: List<CommunityReportSummary>,
    val windowMinutes: Int,
    val cooldownSeconds: Int,
    val persistent: Boolean,
    val votingEnabled: Boolean,
    val summary: CommunityReportSummary,
)

@Serializable
data class ApiErrorResponse(
    val error: String,
)
