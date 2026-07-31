package com.localstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localstream.app.LocalStreamApplication
import com.localstream.app.R
import com.localstream.app.ui.components.TopBar
import com.localstream.app.ui.settings.SettingsViewModel
import com.localstream.app.ui.theme.Black
import com.localstream.app.ui.theme.Red600
import com.localstream.app.ui.theme.White
import com.localstream.app.ui.theme.Zinc800
import com.localstream.app.ui.theme.Zinc900

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as LocalStreamApplication).container
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.scanInstalledPlayers(context)
    }

    var tmdbKeyInput by remember(uiState.tmdbApiKey) { mutableStateOf(uiState.tmdbApiKey) }
    var osKeyInput by remember(uiState.osApiKey) { mutableStateOf(uiState.osApiKey) }
    var osUserInput by remember(uiState.osUsername) { mutableStateOf(uiState.osUsername) }
    var osPassInput by remember(uiState.osPassword) { mutableStateOf(uiState.osPassword) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
    ) {
        TopBar(
            solid = true,
            showSearch = false,
            isFetchingMetadata = false,
            onLogoClick = onLogoClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Section TMDB
            Card(colors = CardDefaults.cardColors(containerColor = Zinc900), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Red600)
                        Text(
                            text = stringResource(R.string.settings_tmdb_title),
                            color = White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tmdbKeyInput,
                        onValueChange = {
                            tmdbKeyInput = it
                            viewModel.saveTmdbApiKey(it)
                        },
                        label = { Text(stringResource(R.string.settings_tmdb_api_key_label), color = White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red600,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { viewModel.testTmdbApiKey(tmdbKeyInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = Red600),
                            shape = RoundedCornerShape(4.dp),
                            enabled = !uiState.isTestingTmdbKey,
                        ) {
                            if (uiState.isTestingTmdbKey) {
                                CircularProgressIndicator(color = White, modifier = Modifier.height(16.dp))
                            } else {
                                Text(stringResource(R.string.settings_tmdb_test_key_button), color = White)
                            }
                        }

                        uiState.tmdbTestResult?.let { msg ->
                            val isOk = msg.contains("valide")
                            Text(
                                text = msg,
                                color = if (isOk) White else Red600,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Section OpenSubtitles
            Card(colors = CardDefaults.cardColors(containerColor = Zinc900), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Subtitles, contentDescription = null, tint = Red600)
                        Text(
                            text = stringResource(R.string.settings_opensubtitles_title),
                            color = White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = osKeyInput,
                        onValueChange = {
                            osKeyInput = it
                            viewModel.saveOsCredentials(it, osUserInput, osPassInput)
                        },
                        label = { Text(stringResource(R.string.settings_opensubtitles_api_key_label), color = White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red600,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = osUserInput,
                        onValueChange = {
                            osUserInput = it
                            viewModel.saveOsCredentials(osKeyInput, it, osPassInput)
                        },
                        label = { Text(stringResource(R.string.settings_opensubtitles_username_label), color = White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red600,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = osPassInput,
                        onValueChange = {
                            osPassInput = it
                            viewModel.saveOsCredentials(osKeyInput, osUserInput, it)
                        },
                        label = { Text(stringResource(R.string.settings_opensubtitles_password_label), color = White.copy(alpha = 0.6f)) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Red600,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = viewModel::loginOpenSubtitles,
                            colors = ButtonDefaults.buttonColors(containerColor = Red600),
                            shape = RoundedCornerShape(4.dp),
                            enabled = !uiState.isLoggingInOs,
                        ) {
                            if (uiState.isLoggingInOs) {
                                CircularProgressIndicator(color = White, modifier = Modifier.height(16.dp))
                            } else {
                                Text(stringResource(R.string.settings_opensubtitles_login_button), color = White)
                            }
                        }

                        val isConnected = uiState.osLoginStatus == "Connecté" 
                        Text(
                            text = stringResource(R.string.settings_opensubtitles_status, uiState.osLoginStatus),
                            color = if (isConnected) White else Red600,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Section Lecteur Vid?o
            Card(colors = CardDefaults.cardColors(containerColor = Zinc900), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = Red600)
                        Text(
                            text = stringResource(R.string.settings_player_title),
                            color = White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.playerMode == "internal",
                            onClick = { viewModel.setPlayerMode("internal") },
                            label = { Text(stringResource(R.string.settings_player_mode_internal)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Red600,
                                selectedLabelColor = White,
                                containerColor = Zinc800,
                                labelColor = White,
                            ),
                        )
                        FilterChip(
                            selected = uiState.playerMode == "external",
                            onClick = { viewModel.setPlayerMode("external") },
                            label = { Text(stringResource(R.string.settings_player_mode_external)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Red600,
                                selectedLabelColor = White,
                                containerColor = Zinc800,
                                labelColor = White,
                            ),
                        )
                    }

                    if (uiState.playerMode == "external" && uiState.installedPlayers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                        ) {
                            val defaultChoice = stringResource(R.string.settings_player_choose_app)
                            val selectedText = uiState.installedPlayers.find {
                                it.packageName == uiState.selectedExternalPlayer
                            }?.appName ?: defaultChoice

                            OutlinedTextField(
                                value = selectedText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_player_external_app_label), color = White.copy(alpha = 0.6f)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White,
                                    unfocusedTextColor = White,
                                    focusedBorderColor = Red600,
                                    unfocusedBorderColor = White.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(Zinc800),
                            ) {
                                uiState.installedPlayers.forEach { player ->
                                    DropdownMenuItem(
                                        text = { Text(player.appName, color = White) },
                                        onClick = {
                                            viewModel.setSelectedExternalPlayer(player.packageName)
                                            dropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Syst?me
            Button(
                onClick = { viewModel.openSystemAppSettings(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Zinc800),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = White)
                Text(stringResource(R.string.settings_system_apps_settings), color = White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
