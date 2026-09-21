package com.aimforge.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimforge.app.ui.theme.AF
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(AF.GlassTop, AF.GlassBottom)))
        .border(1.dp, AF.Border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(18.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AF.Accent,
            contentColor = AF.Bg,
            disabledContainerColor = AF.Track,
            disabledContentColor = AF.TextSecondary
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AF.TextSecondary,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun afFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AF.Accent,
    unfocusedBorderColor = AF.Border,
    focusedLabelColor = AF.Accent,
    unfocusedLabelColor = AF.TextSecondary,
    cursorColor = AF.Accent
)

/** The one bold element: score inside a crosshair reticle ring. null score = empty ring, "--". */
@Composable
fun ReticleScore(score: Int?, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = (score ?: 0) / 100f,
        animationSpec = tween(900),
        label = "reticleScore"
    )
    Box(modifier = modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = 20.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = AF.Track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
            )
            drawArc(
                color = AF.Accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val r = size.minDimension / 2f
            val tick = 14.dp.toPx()
            listOf(0.0, 90.0, 180.0, 270.0).forEach { deg ->
                val rad = Math.toRadians(deg)
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                drawLine(
                    color = AF.TextSecondary,
                    start = Offset(center.x + dx * (r - tick), center.y + dy * (r - tick)),
                    end = Offset(center.x + dx * r, center.y + dy * r),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score?.toString() ?: "--",
                fontSize = 58.sp,
                fontWeight = FontWeight.Light,
                color = AF.TextPrimary
            )
            Text(text = "/ 100", fontSize = 13.sp, color = AF.TextSecondary)
        }
    }
}

/** Small status pill: READY, WAITING, CAPTURED, NOT AVAILABLE, ANALYSIS PENDING, ANALYZED... */
@Composable
fun StatusBadge(text: String, color: androidx.compose.ui.graphics.Color = AF.Accent, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AF.TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = AF.TextPrimary,
            modifier = Modifier.padding(start = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
