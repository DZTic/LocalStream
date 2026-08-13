package com.localstream.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc900

@Composable
fun GestureFeedbackCard(
    feedback: GestureFeedback,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900.copy(alpha = 0.9f)),
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (feedback.type) {
                    FeedbackType.VOLUME -> Icons.Filled.VolumeUp
                    FeedbackType.BRIGHTNESS -> Icons.Filled.Brightness6
                    FeedbackType.SEEK_FORWARD -> Icons.Filled.Forward10
                    FeedbackType.SEEK_REWIND -> Icons.Filled.Replay10
                },
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(28.dp),
            )
            Text(text = feedback.text, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
