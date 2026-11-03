package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ExtractedLead

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeadsListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val leads by viewModel.allLeads.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, SYNCED, WEBSITE, CARD
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val filteredLeads = remember(leads, searchQuery, selectedFilter) {
        leads.filter { lead ->
            val matchesQuery = searchQuery.isBlank() ||
                    lead.name.contains(searchQuery, ignoreCase = true) ||
                    lead.company.contains(searchQuery, ignoreCase = true) ||
                    lead.email.contains(searchQuery, ignoreCase = true) ||
                    lead.phone.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "PENDING" -> lead.syncStatus != "SYNCED"
                "SYNCED" -> lead.syncStatus == "SYNCED"
                "WEBSITE" -> lead.source == "WEBSITE"
                "CARD" -> lead.source == "CARD_SCAN"
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats Summary Dashboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(label = "Total Leads", value = "$totalCount")
                StatItem(label = "Synced to Sheets", value = "$syncedCount")
                val pending = totalCount - syncedCount
                StatItem(label = "Pending Sync", value = "$pending")
            }
        }

        // Action Row: 1-Tap Offline Excel Download & Batch Sync
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val targets = if (filteredLeads.size < leads.size && filteredLeads.isNotEmpty()) filteredLeads else leads
                    viewModel.downloadExcelOffline(context, targets, isCsv = false)
                },
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("download_excel_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF107C41) // Microsoft Excel green
                )
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Download Excel", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.syncAllPendingLeads() },
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("batch_sync_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync")
            }

            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.testTag("export_options_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.TableChart, contentDescription = "Export Options", modifier = Modifier.size(18.dp))
            }

            if (leads.isNotEmpty()) {
                IconButton(onClick = { showClearConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear DBA",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (showExportDialog) {
            ExportLeadsDialog(
                allLeads = leads,
                filteredLeads = filteredLeads,
                viewModel = viewModel,
                onDismiss = { showExportDialog = false }
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, company, email...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("leads_search_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("All (${leads.size})") }
            )
            FilterChip(
                selected = selectedFilter == "PENDING",
                onClick = { selectedFilter = "PENDING" },
                label = { Text("Pending (${leads.count { it.syncStatus != "SYNCED" }})") }
            )
            FilterChip(
                selected = selectedFilter == "SYNCED",
                onClick = { selectedFilter = "SYNCED" },
                label = { Text("Synced (${leads.count { it.syncStatus == "SYNCED" }})") }
            )
            FilterChip(
                selected = selectedFilter == "WEBSITE",
                onClick = { selectedFilter = "WEBSITE" },
                label = { Text("Websites") }
            )
            FilterChip(
                selected = selectedFilter == "CARD",
                onClick = { selectedFilter = "CARD" },
                label = { Text("Card Scans") }
            )
        }

        // Leads List or Empty State
        if (filteredLeads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (leads.isEmpty()) "No leads extracted yet" else "No matching leads found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (leads.isEmpty()) "Use the AI Scraper or Card Scanner to extract your first contact." else "Try adjusting your search query or filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (leads.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.setTab(AppTab.EXTRACT) }) {
                            Text("Start AI Extraction")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLeads, key = { it.id }) { lead ->
                    LeadCardItem(
                        lead = lead,
                        onEdit = { viewModel.startEditingLead(lead) },
                        onDelete = { viewModel.deleteLead(lead) },
                        onSync = { viewModel.syncLeadToGoogleSheets(lead) },
                        onExport = {
                            try {
                                val uri = viewModel.createExcelShareableUri(context, listOf(lead))
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.ms-excel"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Lead: ${lead.name} - ${lead.company}")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Lead to Excel"))
                            } catch (e: Exception) {
                                // fallback
                            }
                        }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Local DBA?") },
            text = { Text("Are you sure you want to clear all extracted leads from local storage? Any rows already sent to Google Sheets will remain safe in your Google Sheet.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLeads()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeadCardItem(
    lead: ExtractedLead,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Avatar, Name, Company & Sync Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar with initial + source icon
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lead.name.firstOrNull()?.uppercase() ?: "L",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = lead.name.ifBlank { "Unknown Contact" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Icon(
                                imageVector = if (lead.source == "CARD_SCAN") Icons.Default.CameraAlt else Icons.Default.Language,
                                contentDescription = lead.source,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (lead.company.isNotBlank() || lead.jobTitle.isNotBlank()) {
                            Text(
                                text = listOf(lead.jobTitle, lead.company).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Google Sheets Sync Status Chip
                when (lead.syncStatus) {
                    "SYNCED" -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Synced",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    "FAILED" -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .clickable { onSync() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Retry Sync",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSync() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Sync Now",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Contact Info details
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (lead.email.isNotBlank()) {
                    Row(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${lead.email}")
                            }
                            context.startActivity(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(text = lead.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (lead.phone.isNotBlank()) {
                    Row(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${lead.phone}")
                            }
                            context.startActivity(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(text = lead.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Export to Excel",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF107C41)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
