package com.localstream.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

private const val SECONDS_PER_MINUTE = 60

@Composable
fun SleepTimerDialog(
    remainingSeconds: Int?,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timerOptions = listOf(15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Minuteur de veille",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (remainingSeconds != null && remainingSeconds > 0) {
                    val mins = remainingSeconds / SECONDS_PER_MINUTE
                    val secs = remainingSeconds % SECONDS_PER_MINUTE
                    Text(
                        text = "Arrêt prévu dans %02d:%02d".format(mins, secs),
                        color = Red600,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Mettre en pause automatiquement après :",
                    color = White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    timerOptions.take(3).forEach { mins ->
                        OutlinedButton(
                            onClick = { onSetTimer(mins) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = White,
                                containerColor = Zinc800,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${mins}m", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    timerOptions.drop(3).forEach { mins ->
                        OutlinedButton(
                            onClick = { onSetTimer(mins) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = White,
                                containerColor = Zinc800,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${mins}m", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (remainingSeconds != null && remainingSeconds > 0) {
                Button(
                    onClick = onCancelTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                ) {
                    Text("Désactiver", color = White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = White.copy(alpha = 0.7f))
            }
        },
    )
}
