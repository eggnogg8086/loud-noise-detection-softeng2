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
import androidx.compose.ui.text.font.FontWeight
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
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Level bar with color gradient (Green -> Yellow -> Red) fixed to container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
            
            // Hider box to "unveil" the gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - animatedLevel)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${minDb.toInt()} dB", fontSize = 14.sp)
            Text("${db.toInt()} dB", fontSize = 24.sp, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${maxDb.toInt()} dB", fontSize = 14.sp)
        }
    }
}
