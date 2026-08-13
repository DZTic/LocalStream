package com.localstream.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.domain.YoutubeUtils
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@Composable
fun YouTubeUrlDialog(
    onDismiss: () -> Unit,
    onPlayYouTube: (String) -> Unit,
) {
    var urlText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val isValid = remember(urlText) { YoutubeUtils.isYoutubeUrlOrId(urlText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
        title = {
            Text(
                text = "Lire une vid?o YouTube",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Collez ou saisissez l'URL d'une vid?o YouTube (ou son ID) pour la lire directement.",
                    color = Zinc500,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://www.youtube.com/watch?v=...", color = Zinc500) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.let { urlText = it.text }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Coller depuis le presse-papier",
                                tint = White,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Red600,
                        unfocusedBorderColor = Zinc800,
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = Red600,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onPlayYouTube(urlText.trim())
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Red600,
                    disabledContainerColor = Zinc800,
                    contentColor = White,
                    disabledContentColor = Zinc500,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Lire", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Zinc500)
            }
        },
    )
}
