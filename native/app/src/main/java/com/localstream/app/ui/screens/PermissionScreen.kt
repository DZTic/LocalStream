package com.localstream.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localstream.app.ui.permission.StoragePermissions
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc500
import com.localstream.app.ui.theme.Zinc800

/**
 * Écran bloquant de demande d'accès au stockage (Phase 7, branché sur la
 * Phase 3 — équivalent Compose de `PermissionGate.tsx`). Tant que la
 * permission n'est pas accordée, le scan MediaStore ne peut pas démarrer.
 */
@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            onPermissionGranted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = Red600,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 24.dp),
        )
        Text(
            text = "Accès au stockage",
            color = White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "LocalStream a besoin d'accéder à vos dossiers pour scanner et afficher vos vidéos. Veuillez accorder les permissions nécessaires pour continuer.",
            color = Zinc500,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(
            onClick = { launcher.launch(StoragePermissions.required) },
            colors = ButtonDefaults.buttonColors(containerColor = Red600, contentColor = White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "ACCORDER L'ACCÈS",
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(text = "Ouvrir les réglages système", color = White, fontSize = 12.sp)
        }
        Text(
            text = "Si la demande n'apparaît plus, activez la permission dans les réglages système.",
            color = Zinc500.copy(alpha = 0.6f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
