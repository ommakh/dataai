package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExtractionPreset

@Composable
fun AgentConfigScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentConfig by viewModel.agentConfig.collectAsStateWithLifecycle()
    val tesseractStatus by viewModel.tesseractStatus.collectAsStateWithLifecycle()

    var agentName by remember(currentConfig) { mutableStateOf(currentConfig.agentName) }
    var agentPersona by remember(currentConfig) { mutableStateOf(currentConfig.agentPersona) }
    var spreadsheetName by remember(currentConfig) { mutableStateOf(currentConfig.spreadsheetName) }
    var extractionDepth by remember(currentConfig) { mutableStateOf(currentConfig.extractionDepth) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Google ML Kit Free On-Device OCR Header Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF107C41).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = Color(0xFF107C41),
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column {
                    Text(
                        text = "Offline OCR Scanner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Fast on-device text recognition. Recognizes English business cards up to 500+ cards/day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // OCR Engine Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = null,
                        tint = Color(0xFF107C41)
                    )
                    Text(
                        text = "OCR Engine Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Text(
                    text = "● $tesseractStatus",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF107C41)
                )

                Text(
                    text = "• Text Recognition: On-Device Latin (English Only)\n" +
                        "• Daily Capacity: Unlimited (500+ cards/day)\n" +
                        "• Privacy: Direct-to-Excel (.xlsx & .csv) in Downloads — Zero Database Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = { viewModel.syncTesseractOfflineLanguageData() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verify OCR Engine Ready")
                }
            }
        }

        // Quick Presets Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Offline Scanner Presets",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                ExtractionPreset.values().forEach { preset ->
                    OutlinedCard(
                        onClick = { viewModel.applyPreset(preset) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Custom OCR & Excel Output Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Scanner Profile & Excel Sheet Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text("Scanner Profile Name") },
                    leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = agentPersona,
                    onValueChange = { agentPersona = it },
                    label = { Text("Scanner Mode Description") },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = spreadsheetName,
                    onValueChange = { spreadsheetName = it },
                    label = { Text("Default Excel (.xlsx) Sheet Title") },
                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "OCR Parsing Depth",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("FAST", "DETAILED", "EXHAUSTIVE").forEach { depth ->
                        FilterChip(
                            selected = extractionDepth == depth,
                            onClick = { extractionDepth = depth },
                            label = { Text(depth.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingIcon = if (extractionDepth == depth) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val updated = currentConfig.copy(
                    agentName = agentName.trim(),
                    agentPersona = agentPersona.trim(),
                    spreadsheetName = spreadsheetName.trim().ifBlank { "Extracted Contacts" },
                    extractionDepth = extractionDepth,
                    customApiKey = ""
                )
                viewModel.updateAgentConfig(updated)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_agent_config_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Offline OCR Settings")
        }
    }
}
