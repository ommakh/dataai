package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.ExtractedLead
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportLeadsDialog(
    allLeads: List<ExtractedLead>,
    filteredLeads: List<ExtractedLead>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var exportScope by remember {
        mutableStateOf(
            if (filteredLeads.size < allLeads.size && filteredLeads.isNotEmpty()) "FILTERED" else "ALL"
        )
    }

    val activeLeads = if (exportScope == "FILTERED") filteredLeads else allLeads
    val dateStamp = remember {
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    }

    // Storage Access Framework launcher for Excel .xlsx file saving
    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.writeLeadsToDocumentUri(context, uri, isExcel = true, leads = activeLeads)
            Toast.makeText(context, "Saved Excel workbook (.xlsx) to device!", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Storage Access Framework launcher for CSV file saving
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.writeLeadsToDocumentUri(context, uri, isExcel = false, leads = activeLeads)
            Toast.makeText(context, "Saved CSV sheet to device!", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("export_leads_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF107C41)), // Official Excel Brand Green
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Export Leads to Excel",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Choose format and export destination",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Scope Selector (if filtered list differs)
                if (filteredLeads.size < allLeads.size) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Select leads to export:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = exportScope == "ALL",
                                    onClick = { exportScope = "ALL" },
                                    label = { Text("All Leads (${allLeads.size})") }
                                )
                                FilterChip(
                                    selected = exportScope == "FILTERED",
                                    onClick = { exportScope = "FILTERED" },
                                    label = { Text("Filtered Results (${filteredLeads.size})") }
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ready to export ${activeLeads.size} leads with full contact intelligence",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = "OFFLINE EXCEL DOWNLOAD & EXPORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Option 1: 1-Tap Download Excel (.xlsx) directly to device Downloads (Offline)
                ExportOptionCard(
                    icon = Icons.Default.Download,
                    iconBgColor = Color(0xFF107C41),
                    title = "Download Excel Sheet (.xlsx)",
                    badge = "GENUINE EXCEL FORMAT",
                    description = "Native OpenXML workbook (.xlsx) with styled green headers, cell borders, and column widths. Opens offline smoothly in Microsoft Excel, Google Sheets, or LibreOffice.",
                    onClick = {
                        viewModel.downloadExcelOffline(context, activeLeads, isCsv = false)
                        onDismiss()
                    }
                )

                // Option 2: 1-Tap Download CSV (.csv) directly to device Downloads (Offline)
                ExportOptionCard(
                    icon = Icons.Default.FileDownload,
                    iconBgColor = Color(0xFF0D9488),
                    title = "Download CSV Sheet (.csv)",
                    badge = "UNIVERSAL COMPATIBILITY",
                    description = "Directly saves UTF-8 BOM CSV into Downloads folder. Seamlessly opens offline on every phone and spreadsheet app without formatting restrictions.",
                    onClick = {
                        viewModel.downloadExcelOffline(context, activeLeads, isCsv = true)
                        onDismiss()
                    }
                )

                // Option 3: Save Excel (.xlsx) via Storage File Picker
                ExportOptionCard(
                    icon = Icons.Default.Folder,
                    iconBgColor = Color(0xFF0284C7),
                    title = "Save via Folder Picker (.xlsx)",
                    description = "Choose a custom destination (Documents, SD Card, or cloud drive) via the Android system file picker.",
                    onClick = {
                        saveExcelLauncher.launch("extracted_leads_$dateStamp.xlsx")
                    }
                )

                // Option 4: Share Excel (.xlsx) file to Apps
                ExportOptionCard(
                    icon = Icons.Default.Share,
                    iconBgColor = Color(0xFF6366F1),
                    title = "Share / Open in Excel App (.xlsx)",
                    description = "Sends the genuine Excel workbook directly to installed apps like Microsoft Excel, Google Sheets, Gmail, or Slack.",
                    onClick = {
                        try {
                            val uri = viewModel.createExcelShareableUri(context, activeLeads)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Extracted Leads - DataExtract AI (${activeLeads.size} contacts)")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newRawUri("Extracted Leads", uri)
                            }
                            val chooser = Intent.createChooser(shareIntent, "Open or Share Excel Sheet").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(chooser)
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                // Option 5: Copy to Clipboard for Excel
                ExportOptionCard(
                    icon = Icons.Default.ContentCopy,
                    iconBgColor = Color(0xFF475569),
                    title = "Copy for Excel (Clipboard)",
                    description = "Copies tab-delimited cells so you can paste directly into an open Microsoft Excel or Google Sheets grid.",
                    onClick = {
                        val tsv = viewModel.getExportTsvString(activeLeads)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Extracted Leads TSV", tsv)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied ${activeLeads.size} lead rows! Paste directly into Excel.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                )

                // Footer Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    description: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF107C41))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
