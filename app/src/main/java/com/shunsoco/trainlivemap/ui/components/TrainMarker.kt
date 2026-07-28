package com.shunsoco.trainlivemap.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.domain.train.TrainFace
import com.shunsoco.trainlivemap.domain.train.directionLabelJa
import com.shunsoco.trainlivemap.domain.train.resolveFace
import com.shunsoco.trainlivemap.domain.train.trainContentDescription
import com.shunsoco.trainlivemap.ui.theme.DelayRed
import com.shunsoco.trainlivemap.ui.theme.SuspendedBlue

/**
 * Reusable, status-aware front-facing train marker.
 *
 * The 72 x 86 dp surface leaves at least a 48 dp hit target around the visual
 * body. Direction and status are always expressed using words/symbols as well
 * as color.
 */
@Composable
fun TrainMarker(
    train: TrainLocation,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val face = train.resolveFace()
    val lineColor = parseLineColor(train.lineColor)
    // Canvas labels follow font scale within the fixed marker bounds. TalkBack
    // always exposes the complete uncapped text through semantics.
    val markerTextScale = LocalDensity.current.fontScale.coerceIn(1f, 1.12f)
    Canvas(
        modifier = modifier
            .size(width = 72.dp, height = 86.dp)
            .graphicsLayer {
                val markerScale = if (selected) 1.16f else 1f
                scaleX = markerScale
                scaleY = markerScale
                shadowElevation = if (selected) 12.dp.toPx() else 5.dp.toPx()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = trainContentDescription(train)
                role = Role.Button
            }
            .clickable(
                role = Role.Button,
                onClickLabel = "列車の詳細を表示",
                onClick = onClick,
            ),
    ) {
        drawTrainMarker(
            lineColor = lineColor,
            face = face,
            direction = train.direction,
            delayMinutes = train.delayMinutes,
            suspended = train.status == TrainStatus.SUSPENDED,
            textScale = markerTextScale,
        )
    }
}

private fun DrawScope.drawTrainMarker(
    lineColor: Color,
    face: TrainFace,
    direction: TrainDirection,
    delayMinutes: Int,
    suspended: Boolean,
    textScale: Float,
) {
    val density = size.width / 72f
    fun px(value: Float): Float = value * density

    val badgeText = when {
        suspended -> "見合わせ"
        delayMinutes > 0 -> "+${delayMinutes}分"
        else -> null
    }
    if (badgeText != null) {
        val badgeColor = if (suspended) Color(0xFF173B64) else DelayRed
        drawRoundRect(
            color = badgeColor,
            topLeft = Offset(px(11f), px(1f)),
            size = Size(px(50f), px(17f)),
            cornerRadius = CornerRadius(px(8.5f)),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px(11f), px(1f)),
            size = Size(px(50f), px(17f)),
            cornerRadius = CornerRadius(px(8.5f)),
            style = Stroke(width = px(1.5f)),
        )
        drawCenteredText(
            text = badgeText,
            centerX = px(36f),
            baselineY = px(13.5f),
            textSize = px(if (suspended) 9f else 10f) * textScale,
            color = Color.White,
        )
    }

    val top = px(19f)
    val bodyRect = Rect(
        left = px(15f),
        top = top,
        right = px(57f),
        bottom = px(61f),
    )
    drawRoundRect(
        color = Color(0xFFFFFDF9),
        topLeft = Offset(bodyRect.left - px(2.5f), bodyRect.top - px(2.5f)),
        size = Size(bodyRect.width + px(5f), bodyRect.height + px(5f)),
        cornerRadius = CornerRadius(px(12f)),
    )
    drawRoundRect(
        color = lineColor,
        topLeft = bodyRect.topLeft,
        size = bodyRect.size,
        cornerRadius = CornerRadius(px(10f)),
    )
    drawRoundRect(
        color = Color(0xFF493B38),
        topLeft = bodyRect.topLeft,
        size = bodyRect.size,
        cornerRadius = CornerRadius(px(10f)),
        style = Stroke(width = px(1.3f)),
    )

    val windowRect = Rect(
        left = px(20f),
        top = px(25f),
        right = px(52f),
        bottom = px(45f),
    )
    drawRoundRect(
        color = Color(0xFFFFFAF7),
        topLeft = windowRect.topLeft,
        size = windowRect.size,
        cornerRadius = CornerRadius(px(6f)),
    )
    drawRoundRect(
        color = Color(0xFF493B38),
        topLeft = windowRect.topLeft,
        size = windowRect.size,
        cornerRadius = CornerRadius(px(6f)),
        style = Stroke(width = px(1f)),
    )

    drawFace(face, ::px)

    drawCircle(Color(0xFFFFAFC2), radius = px(2.5f), center = Offset(px(23f), px(53f)))
    drawCircle(Color(0xFFFFAFC2), radius = px(2.5f), center = Offset(px(49f), px(53f)))
    drawLine(
        color = Color(0xFF493B38),
        start = Offset(px(30f), px(54f)),
        end = Offset(px(42f), px(54f)),
        strokeWidth = px(1.4f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color(0xFF493B38),
        start = Offset(px(22f), px(61f)),
        end = Offset(px(18f), px(66f)),
        strokeWidth = px(2f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color(0xFF493B38),
        start = Offset(px(50f), px(61f)),
        end = Offset(px(54f), px(66f)),
        strokeWidth = px(2f),
        cap = StrokeCap.Round,
    )

    val directionColor = if (direction == TrainDirection.INBOUND) {
        Color(0xFF1E3A8A)
    } else {
        Color(0xFF7C2D12)
    }
    drawRoundRect(
        color = directionColor,
        topLeft = Offset(px(14f), px(68f)),
        size = Size(px(44f), px(16f)),
        cornerRadius = CornerRadius(px(8f)),
    )
    drawRoundRect(
        color = if (direction == TrainDirection.INBOUND) {
            Color(0xFF93C5FD)
        } else {
            Color(0xFFFDBA74)
        },
        topLeft = Offset(px(14f), px(68f)),
        size = Size(px(44f), px(16f)),
        cornerRadius = CornerRadius(px(8f)),
        style = Stroke(width = px(1f)),
    )
    drawCenteredText(
        text = directionLabelJa(direction),
        centerX = px(36f),
        baselineY = px(79.5f),
        textSize = px(9.5f) * textScale,
        color = Color.White,
    )
}

private fun DrawScope.drawFace(
    face: TrainFace,
    px: (Float) -> Float,
) {
    val ink = Color(0xFF493B38)
    when (face) {
        TrainFace.NORMAL -> {
            drawCircle(ink, radius = px(1.7f), center = Offset(px(28f), px(34f)))
            drawCircle(ink, radius = px(1.7f), center = Offset(px(44f), px(34f)))
            drawArc(
                color = ink,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(px(31f), px(34f)),
                size = Size(px(10f), px(7f)),
                style = Stroke(width = px(1.3f), cap = StrokeCap.Round),
            )
        }

        TrainFace.DELAYED -> {
            drawLine(
                ink,
                Offset(px(24f), px(31f)),
                Offset(px(31f), px(29f)),
                px(1.3f),
                StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(px(41f), px(29f)),
                Offset(px(48f), px(31f)),
                px(1.3f),
                StrokeCap.Round,
            )
            drawCircle(ink, radius = px(1.5f), center = Offset(px(28f), px(35f)))
            drawCircle(ink, radius = px(1.5f), center = Offset(px(44f), px(35f)))
            drawArc(
                color = ink,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(px(31f), px(38f)),
                size = Size(px(10f), px(6f)),
                style = Stroke(width = px(1.3f), cap = StrokeCap.Round),
            )
        }

        TrainFace.SUSPENDED -> {
            drawLine(
                ink,
                Offset(px(24f), px(31f)),
                Offset(px(31f), px(29f)),
                px(1.3f),
                StrokeCap.Round,
            )
            drawLine(
                ink,
                Offset(px(41f), px(29f)),
                Offset(px(48f), px(31f)),
                px(1.3f),
                StrokeCap.Round,
            )
            drawArc(
                color = ink,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(px(25f), px(33f)),
                size = Size(px(6f), px(3f)),
                style = Stroke(width = px(1.2f), cap = StrokeCap.Round),
            )
            drawArc(
                color = ink,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(px(41f), px(33f)),
                size = Size(px(6f), px(3f)),
                style = Stroke(width = px(1.2f), cap = StrokeCap.Round),
            )
            drawArc(
                color = ink,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(px(31f), px(38f)),
                size = Size(px(10f), px(6f)),
                style = Stroke(width = px(1.3f), cap = StrokeCap.Round),
            )
            drawLine(
                SuspendedBlue,
                Offset(px(28f), px(37f)),
                Offset(px(28f), px(41f)),
                px(1.6f),
                StrokeCap.Round,
            )
            drawCircle(
                SuspendedBlue,
                radius = px(1.3f),
                center = Offset(px(28f), px(42.5f)),
            )
            drawLine(
                SuspendedBlue,
                Offset(px(44f), px(37f)),
                Offset(px(44f), px(41f)),
                px(1.6f),
                StrokeCap.Round,
            )
            drawCircle(
                SuspendedBlue,
                radius = px(1.3f),
                center = Offset(px(44f), px(42.5f)),
            )
        }
    }
}

private fun DrawScope.drawCenteredText(
    text: String,
    centerX: Float,
    baselineY: Float,
    textSize: Float,
    color: Color,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgbCompat()
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.nativeCanvas.drawText(text, centerX, baselineY, paint)
    }
}

private fun parseLineColor(raw: String): Color = runCatching {
    Color(raw.toColorInt())
}.getOrDefault(Color(0xFFF68B1E))

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
