package com.atv.adbcommander.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atv.adbcommander.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The iconic Tellyscope TV Cookie: A 12-lobed rounded floral / squircle shape
 * with a glowing teal container that houses the TV/projector icon.
 */
@Composable
fun TellyTvCookie(
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    icon: ImageVector = Icons.Default.Tv,
    tint: Color = TellyTealPrimary,
    containerColor: Color = TellyTealDark
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Scalloped / Petaled Cookie Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val outerRadius = (w.coerceAtMost(h) / 2f) * 0.95f
            val innerRadius = outerRadius * 0.86f
            val lobes = 10

            val path = Path()
            val totalPoints = lobes * 4
            for (i in 0 until totalPoints) {
                val angle = (i.toFloat() / totalPoints) * 2f * PI.toFloat()
                val r = if (i % 2 == 0) outerRadius else innerRadius
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(path = path, color = containerColor)
        }

        Icon(
            imageVector = icon,
            contentDescription = "TV Cookie",
            tint = tint,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}

/**
 * Dual-Tone Segmented Progress Bar matching Tellyscope storage & RAM bars.
 */
@Composable
fun TellyDualProgressBar(
    progress: Float, // 0.0f .. 1.0f
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
    fillColor: Color = TellyTealPrimary,
    trackColor: Color = TellyTealDark.copy(alpha = 0.35f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1.0f))
                .clip(RoundedCornerShape(barHeight / 2))
                .background(fillColor)
        )
    }
}

/**
 * Expressive Wavy Progress Bar matching Tellyscope file send dialog.
 */
@Composable
fun TellyWavyProgressBar(
    progress: Float, // 0.0f .. 1.0f
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    waveColor: Color = TellyTealPrimary,
    trackColor: Color = TellyTealDark.copy(alpha = 0.3f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val filledWidth = w * progress.coerceIn(0f, 1f)

        // Draw background track
        drawLine(
            color = trackColor,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 4.dp.toPx()
        )

        // Draw animated wavy line on filled portion
        if (filledWidth > 4f) {
            val wavePath = Path()
            val waveLength = 28.dp.toPx()
            val amplitude = 3.dp.toPx()

            var x = 0f
            wavePath.moveTo(0f, midY + amplitude * sin(phase))
            while (x <= filledWidth) {
                val y = midY + amplitude * sin((x / waveLength) * 2f * PI.toFloat() + phase)
                wavePath.lineTo(x, y)
                x += 3f
            }

            drawPath(
                path = wavePath,
                color = waveColor,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}

/**
 * Standard Compact Card with 16.dp corners.
 */
@Composable
fun TellyCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    containerColor: Color = TellySurfaceContainer,
    borderColor: Color = TellyOutlineSubtle,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                content = content
            )
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                content = content
            )
        }
    }
}

/**
 * Status Chip matching "✔ Running fine" from Tellyscope.
 */
@Composable
fun TellyStatusChip(
    text: String = "Running fine",
    icon: ImageVector = Icons.Default.CheckCircle,
    color: Color = TellySuccess,
    containerColor: Color = TellySuccessContainer
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = TellyOnSurface
        )
    }
}
