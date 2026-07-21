package com.loudnoisedetectionapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StereoVUMeter(
    leftDb: Float,
    rightDb: Float,
    isRejectionActive: Boolean,
    modifier: Modifier = Modifier,
    minDb: Float = 0f,
    maxDb: Float = 100f
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VUMeterBar(
            db = leftDb,
            label = "L",
            isRejectionActive = isRejectionActive,
            minDb = minDb,
            maxDb = maxDb,
            modifier = Modifier.fillMaxWidth()
        )
        VUMeterBar(
            db = rightDb,
            label = "R",
            isRejectionActive = isRejectionActive,
            minDb = minDb,
            maxDb = maxDb,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun VUMeterBar(
    db: Float,
    label: String,
    isRejectionActive: Boolean,
    modifier: Modifier = Modifier,
    minDb: Float = 0f,
    maxDb: Float = 100f
) {
    val normalizedLevel = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
    val animatedLevel by animateFloatAsState(targetValue = normalizedLevel, label = "Level")
    
    val rejectionColor by animateColorAsState(
        targetValue = if (isRejectionActive) Color(0xFF2196F3) else Color.Transparent,
        label = "Rejection"
    )

    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(12.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Gradient Layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Green, Color.Yellow, Color.Red)
                        )
                    )
            )
            
            // Mask Layer
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - animatedLevel)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Rejection Highlight Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(rejectionColor.copy(alpha = 0.4f))
            )
        }
        
        Text(
            text = "${db.toInt()}",
            fontSize = 12.sp,
            modifier = Modifier.width(24.dp)
        )
    }
}

@Composable
fun VUMeter(
    db: Float,
    modifier: Modifier = Modifier,
    minDb: Float = 0f,
    maxDb: Float = 100f
) {
    // Legacy support or single meter fallback
    VUMeterBar(db = db, label = "", isRejectionActive = false, minDb = minDb, maxDb = maxDb, modifier = modifier)
}
