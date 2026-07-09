package com.loudnoisedetectionapp

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun VUMeter(
    db: Float,
    modifier: Modifier = Modifier,
    minDb: Float = 0f,
    maxDb: Float = 100f
) {
    // Normalize dB value for the progress bar (0.0 to 1.0)
    val normalizedLevel = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
    
    val animatedLevel by animateFloatAsState(
        targetValue = normalizedLevel,
        label = "VUMeterAnimation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Level bar with color gradient (Green -> Yellow -> Red)
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Green,
                                Color.Yellow,
                                Color.Red
                            )
                        )
                    )
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${minDb.toInt()} dB", fontSize = 12.sp)
            Text("${db.toInt()} dB", fontSize = 14.sp, style = MaterialTheme.typography.labelLarge)
            Text("${maxDb.toInt()} dB", fontSize = 12.sp)
        }
    }
}
