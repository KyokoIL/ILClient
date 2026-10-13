package app.ilclient.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ILClientTriangleLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    isConnected: Boolean = false,
    isConnecting: Boolean = false,
    reducedMotion: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isConnecting && !reducedMotion) 0.75f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val brandGradient = Brush.linearGradient(
            colors = if (isConnected) {
                listOf(Color(0xFFFFD03D), Color(0xFFFF8A3D), Color(0xFFF044A6))
            } else {
                listOf(Color(0xFFFF8A3D), Color(0xFFF044A6), Color(0xFF8B5CF6))
            },
            start = Offset(w * 0.5f, h * 0.12f),
            end = Offset(w * 0.5f, h * 0.88f)
        )
        drawCircle(
            brush = brandGradient,
            radius = w * 0.46f,
            center = Offset(w / 2f, h / 2f),
            alpha = pulseAlpha,
            style = Stroke(width = w * 0.035f)
        )
        val mainTriangle = Path().apply {
            moveTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.74f)
            lineTo(w * 0.18f, h * 0.74f)
            lineTo(w * 0.28f, h * 0.57f)
            lineTo(w * 0.57f, h * 0.57f)
            lineTo(w * 0.42f, h * 0.31f)
            close()
        }
        val innerTriangle = Path().apply {
            moveTo(w * 0.38f, h * 0.37f)
            lineTo(w * 0.49f, h * 0.53f)
            lineTo(w * 0.30f, h * 0.53f)
            close()
        }
        drawPath(path = mainTriangle, brush = brandGradient, style = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path = mainTriangle, brush = brandGradient)
        drawPath(path = innerTriangle, brush = brandGradient)
    }
}