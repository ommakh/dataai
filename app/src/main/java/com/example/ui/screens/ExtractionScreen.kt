package com.example.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.example.data.local.ExtractedLead
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads EXIF orientation degrees from a content Uri or file path.
 */
private fun getExifOrientationDegrees(context: Context, uri: Uri, fallbackFilePath: String? = null): Int {
    try {
        if (!fallbackFilePath.isNullOrBlank()) {
            val file = File(fallbackFilePath)
            if (file.exists() && file.length() > 0L) {
                val exif = ExifInterface(file.absolutePath)
                return exifOrientationToDegrees(
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                )
            }
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            return exifOrientationToDegrees(
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            )
        }
    } catch (_: Throwable) {
    }
    return 0
}

private fun exifOrientationToDegrees(orientation: Int): Int {
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

private fun applyExifRotation(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    return try {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        rotated
    } catch (_: Throwable) {
        bitmap
    }
}

/**
 * Safely decodes and downsamples high-resolution mobile camera/gallery photos (e.g., 12MP–108MP)
 * so they never exceed [maxDimension] (2048px) or use Bitmap.Config.HARDWARE, and corrects EXIF rotation.
 * Includes automatic fallback to BitmapFactory if ImageDecoder fails on repeated captures.
 */
private fun decodeSafeMobileBitmap(
    context: Context,
    uri: Uri,
    fallbackFilePath: String? = null,
    maxDimension: Int = 2048
): Bitmap? {
    val exifDegrees = getExifOrientationDegrees(context, uri, fallbackFilePath)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > maxDimension && largest > 0) {
                    val scale = maxDimension.toFloat() / largest.toFloat()
                    val targetW = (width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (height * scale).roundToInt().coerceAtLeast(1)
                    decoder.setTargetSize(targetW, targetH)
                }
            }
            if (decoded.width > 0 && decoded.height > 0) {
                // ImageDecoder handles EXIF orientation by default; verify and apply if unrotated
                val finalBmp = if (exifDegrees != 0 && decoded.width > decoded.height && (exifDegrees == 90 || exifDegrees == 270)) {
                    applyExifRotation(decoded, exifDegrees)
                } else {
                    decoded
                }
                return scaleSafeMobileBitmap(finalBmp, maxDimension)
            }
        } catch (_: Exception) {
            // Fall through to BitmapFactory stream / file decoding
        }
    }

    try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        var sampleSize = 1
        val w = boundsOptions.outWidth
        val h = boundsOptions.outHeight
        if (w > 0 && h > 0) {
            while ((w / sampleSize) > maxDimension * 2 || (h / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
        if (decoded != null) {
            val rotated = if (exifDegrees != 0) applyExifRotation(decoded, exifDegrees) else decoded
            return scaleSafeMobileBitmap(rotated, maxDimension)
        }
    } catch (_: Exception) {
    }

    if (!fallbackFilePath.isNullOrBlank()) {
        try {
            val file = File(fallbackFilePath)
            if (file.exists() && file.length() > 0L) {
                val decoded = BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                )
                if (decoded != null) {
                    val rotated = if (exifDegrees != 0) applyExifRotation(decoded, exifDegrees) else decoded
                    return scaleSafeMobileBitmap(rotated, maxDimension)
                }
            }
        } catch (_: Exception) {
        }
    }

    return null
}

private fun scaleSafeMobileBitmap(bitmap: Bitmap, maxDimension: Int = 2048): Bitmap {
    val safeBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
    } else {
        bitmap
    }
    val largest = max(safeBitmap.width, safeBitmap.height)
    if (largest <= maxDimension || largest <= 0) return safeBitmap
    val scale = maxDimension.toFloat() / largest.toFloat()
    val targetW = (safeBitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetH = (safeBitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(safeBitmap, targetW, targetH, true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractionScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val agentConfig by viewModel.agentConfig.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractionStep by viewModel.extractionStep.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastExtractionResult.collectAsStateWithLifecycle()
    val selectedCardBitmap by viewModel.selectedCardBitmap.collectAsStateWithLifecycle()
    val capturedCardBitmaps by viewModel.capturedCardBitmaps.collectAsStateWithLifecycle()
    val cardBatchLeads by viewModel.cardBatchLeads.collectAsStateWithLifecycle()
    val isCurrentPhotoExtracted by viewModel.isCurrentPhotoExtracted.collectAsStateWithLifecycle()
    val selectedDocuments by viewModel.selectedDocuments.collectAsStateWithLifecycle()
    val isBulkExtracting by viewModel.isBulkExtracting.collectAsStateWithLifecycle()
    val bulkProgress by viewModel.bulkProgress.collectAsStateWithLifecycle()
    val bulkProgressCurrent by viewModel.bulkProgressCurrent.collectAsStateWithLifecycle()
    val bulkProgressTotal by viewModel.bulkProgressTotal.collectAsStateWithLifecycle()
    val bulkExtractedLeads by viewModel.bulkExtractedLeads.collectAsStateWithLifecycle()
    val bulkCsvContent by viewModel.bulkCsvContent.collectAsStateWithLifecycle()
    val bulkBatchCompleted by viewModel.bulkBatchCompleted.collectAsStateWithLifecycle()
    val noCardRecognizedAlert by viewModel.noCardRecognizedAlert.collectAsStateWithLifecycle()

    var extractModeTab by remember { mutableIntStateOf(1) } // 0: Website, 1: Card Scanner (Default), 2: Bulk PDF/Docs
    var websiteUrlInput by remember { mutableStateOf("") }
    var rawTextInput by remember { mutableStateOf("") }
    var showRawTextExpander by remember { mutableStateOf(false) }
    var autoDownloadCsvOffline by remember { mutableStateOf(true) }
    var showCsvPreview by remember { mutableStateOf(true) }
    var appendCardMode by remember { mutableStateOf(viewModel.pendingAppendCardMode) }
    var showAddManualCardDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(noCardRecognizedAlert) {
        if (!noCardRecognizedAlert.isNullOrBlank()) {
            Toast.makeText(context, noCardRecognizedAlert, Toast.LENGTH_LONG).show()
        }
    }

    // Temp file Uri for high-res camera capture (also persisted in ViewModel across rotation/recreation)
    var photoUri by remember { mutableStateOf(viewModel.pendingCameraUri) }

    // TakePicture launcher (saves to file Uri, downsampled safely for mobile)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val capturedUri = photoUri ?: viewModel.pendingCameraUri
        val capturedPath = viewModel.pendingCameraFilePath
        val shouldAppend = viewModel.pendingAppendCardMode
        if (success && (capturedUri != null || !capturedPath.isNullOrBlank())) {
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    if (capturedUri != null) {
                        decodeSafeMobileBitmap(context, capturedUri, fallbackFilePath = capturedPath, maxDimension = 2048)
                    } else if (!capturedPath.isNullOrBlank()) {
                        BitmapFactory.decodeFile(capturedPath)?.let { scaleSafeMobileBitmap(it, 2048) }
                    } else {
                        null
                    }
                }
                if (bitmap != null) {
                    viewModel.setCardBitmap(bitmap)
                    viewModel.extractFromCardImage(bitmap, appendToExisting = shouldAppend)
                } else {
                    viewModel.setBannerMessage("Could not read captured photo. Please try again.")
                }
            }
        }
    }

    // Camera launcher preview fallback (small thumbnail)
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val safeBmp = scaleSafeMobileBitmap(bitmap, maxDimension = 2048)
            viewModel.setCardBitmap(safeBmp)
            viewModel.extractFromCardImage(safeBmp, appendToExisting = viewModel.pendingAppendCardMode)
        }
    }

    fun launchCameraFlow() {
        try {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val photoFile = File.createTempFile("camera_card_${System.currentTimeMillis()}_", ".jpg", cameraDir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            photoUri = uri
            viewModel.pendingCameraUri = uri
            viewModel.pendingCameraFilePath = photoFile.absolutePath
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            // Fallback to preview capture if file provider or camera intent fails on mobile
            try {
                cameraPreviewLauncher.launch()
            } catch (ex: Exception) {
                viewModel.setBannerMessage("Unable to open camera on this device: ${ex.message}")
            }
        }
    }

    // Camera Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraFlow()
        } else {
            viewModel.setBannerMessage("Camera permission is needed to scan cards. You can also pick an image from your gallery.")
        }
    }

    fun onCameraClick(append: Boolean = false) {
        appendCardMode = append
        viewModel.pendingAppendCardMode = append
        ensureNotificationPermission()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCameraFlow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Single-Photo / Multi-Photo picker launcher (Selects 1 photo with up to 20 cards or up to 50 photos)
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val shouldAppend = viewModel.pendingAppendCardMode
            coroutineScope.launch {
                val loadedBitmaps = withContext(Dispatchers.IO) {
                    val list = mutableListOf<Bitmap>()
                    for (uri in uris.take(50)) {
                        val bmp = decodeSafeMobileBitmap(context, uri, maxDimension = 2048)
                        if (bmp != null) {
                            list.add(bmp)
                        }
                    }
                    list
                }
                if (loadedBitmaps.isNotEmpty()) {
                    viewModel.extractFromMultipleCardImages(loadedBitmaps, appendToExisting = shouldAppend)
                } else {
                    viewModel.setBannerMessage("Error loading selected card image(s) from gallery.")
                }
            }
        }
    }

    // Fallback single image picker if PickMultipleVisualMedia fails on older mobile ROMs
    val fallbackSinglePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val shouldAppend = viewModel.pendingAppendCardMode
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSafeMobileBitmap(context, uri, maxDimension = 2048)
                }
                if (bitmap != null) {
                    viewModel.extractFromCardImage(bitmap, appendToExisting = shouldAppend)
                } else {
                    viewModel.setBannerMessage("Error loading selected photo.")
                }
            }
        }
    }

    fun launchPhotoPicker(append: Boolean = false) {
        appendCardMode = append
        viewModel.pendingAppendCardMode = append
        ensureNotificationPermission()
        try {
            multiPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            try {
                fallbackSinglePhotoLauncher.launch("image/*")
            } catch (ex: Exception) {
                viewModel.setBannerMessage("Could not open photo gallery: ${ex.message}")
            }
        }
    }

    // Document Picker launcher for Word (.docx, .doc), PDF (.pdf), TXT files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            viewModel.addDocumentsFromUris(context, uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Offline Tesseract OCR Status Badge
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF107C41).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = "Card & Document Scanner",
                            tint = Color(0xFF107C41),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Card & Document Scanner",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "English • Direct to Excel (.xlsx)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = { viewModel.setTab(AppTab.AGENT) }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Configure Offline OCR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Mode Segment Tabs (Website vs Card vs Bulk Word/PDF)
        TabRow(
            selectedTabIndex = extractModeTab,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = extractModeTab == 0,
                onClick = { extractModeTab = 0 },
                text = { Text("Website → Excel") },
                icon = { Icon(Icons.Default.Language, contentDescription = null) }
            )
            Tab(
                selected = extractModeTab == 1,
                onClick = { extractModeTab = 1 },
                text = { Text("Card → Excel") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
            Tab(
                selected = extractModeTab == 2,
                onClick = { extractModeTab = 2 },
                text = { Text("PDF → Excel") },
                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
            )
        }

        // Mode 0: Website Scraper View
        if (extractModeTab == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Website Extraction Target",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    OutlinedTextField(
                        value = websiteUrlInput,
                        onValueChange = { websiteUrlInput = it },
                        label = { Text("Website URL (e.g. company/contact)") },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        },
                        trailingIcon = {
                            Row {
                                if (websiteUrlInput.isNotEmpty()) {
                                    IconButton(onClick = { websiteUrlInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            websiteUrlInput = clip.getItemAt(0).text.toString()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("website_url_input"),
                        singleLine = true
                    )

                    // Raw text expander toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRawTextExpander = !showRawTextExpander }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showRawTextExpander) "▼ Hide Raw Web Text / HTML Input" else "▶ Paste Raw Web Text / HTML directly",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showRawTextExpander) {
                        OutlinedTextField(
                            value = rawTextInput,
                            onValueChange = { rawTextInput = it },
                            label = { Text("Paste Raw HTML or copied web content") },
                            placeholder = { Text("Paste contact page text, executive bios, or footer text...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 5
                        )
                    }

                    Button(
                        onClick = {
                            if (showRawTextExpander && rawTextInput.isNotBlank()) {
                                viewModel.extractFromWebsite(websiteUrlInput.ifBlank { "https://pasted-content.local" })
                            } else {
                                viewModel.extractFromWebsite(websiteUrlInput)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_extraction_button"),
                        enabled = !isExtracting,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Converting Website to Excel (.xlsx)...")
                        } else {
                            Icon(Icons.Default.TableChart, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Convert Website Contact to Excel (.xlsx)")
                        }
                    }
                }
            }
        } else if (extractModeTab == 1) {
            // Mode 1: Card Scanner (Camera) View
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1-Photo Card Scanner (Up to 20 Cards → Excel)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF107C41).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Auto-Excel (.xlsx)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF107C41),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "Click or select 1 photo in which up to 20 English business cards are placed on a table. Extracts all card details—including repeated cards—and converts them directly into an Excel (.xlsx) file without saving any data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    // Active Image Preview Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedCardBitmap != null) {
                            Image(
                                bitmap = selectedCardBitmap!!.asImageBitmap(),
                                contentDescription = "Captured Business Card Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Click 1 photo (with up to 20 cards) to extract into Excel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 1–50 Captured Card Images Thumbnail Queue
                    if (capturedCardBitmaps.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Captured Card Images (${capturedCardBitmaps.size} of 50):",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = { viewModel.clearCardBatch() }
                                ) {
                                    Text("Clear All", fontSize = 12.sp)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                capturedCardBitmaps.forEachIndexed { index, bmp ->
                                    val isSelected = selectedCardBitmap === bmp
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setCardBitmap(bmp) }
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Captured Card #${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .clickable { viewModel.removeCapturedImageAt(index) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Remove Image #${index + 1}",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Primary Action Buttons: Click 1 Photo (Camera) or Select Photo (Gallery) -> Auto Excel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onCameraClick(append = false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("camera_scan_button"),
                            enabled = !isExtracting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Click 1 Photo (≤20 Cards)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                launchPhotoPicker(append = false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("gallery_pick_button"),
                            enabled = !isExtracting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Upload Photo → Excel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // "+ Add More Images / Cards to Batch (Up to 50 Images)" Panel
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_more_cards_panel"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Add More Images (1–50) or Cards (${cardBatchLeads.size} extracted)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        onCameraClick(append = true)
                                    },
                                    enabled = !isExtracting && capturedCardBitmaps.size < 50,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_card_camera_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("+ Capture Next (${capturedCardBitmaps.size}/50)", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        launchPhotoPicker(append = true)
                                    },
                                    enabled = !isExtracting && capturedCardBitmaps.size < 50,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_card_gallery_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("+ Add Photo Gallery", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    showAddManualCardDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_manual_card_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Add 1 More Card Manually", fontSize = 12.sp)
                            }
                        }
                    }

                    // Prominent Notification Banner when NO card is recognized
                    if (noCardRecognizedAlert != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("no_card_notification_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Notification",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "There is no card",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "No business card was recognized in this image. Please capture or upload a clear photo of a business card.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissNoCardAlert() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Dismiss notification",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Direct Excel Conversion Status Badge (includes repeated cards, no database storage)
                    if (selectedCardBitmap != null && isCurrentPhotoExtracted && cardBatchLeads.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("one_time_extracted_badge"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF047857).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Extracted ${cardBatchLeads.size} card(s) in memory • Zero data saved to storage or database.",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Mode 2: Bulk PDF (5-10 PDFs) & Document Analysis -> Contact Extraction -> CSV Offline Download
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bulk PDF Analysis (5–10 PDFs) → Excel (.xlsx)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Select 5–10 PDF files (or Word docs). Extracts all contact details and converts them directly into Excel (.xlsx) & CSV offline without saving any data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action row: Select 5-10 PDFs Button + Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_bulk_pdfs_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedDocuments.isEmpty()) "Select 5–10 PDFs" else "Add More PDFs (${selectedDocuments.size} selected)"
                            )
                        }

                        if (selectedDocuments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.clearBulkDocuments() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    // Attached Documents Queue
                    if (selectedDocuments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected PDF Queue (${selectedDocuments.size} files ready)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "~${selectedDocuments.sumOf { it.estimatedContactsCount }} contacts est.",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedDocuments.forEachIndexed { index, doc ->
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (doc.fileType.name == "PDF") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (doc.fileType.name == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = if (doc.fileType.name == "PDF") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${doc.fileName}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${doc.fileType.name} • ${doc.fileSizeFormatted} • ~${doc.estimatedContactsCount} contacts",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { viewModel.removeDocument(index) }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Remove file",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Auto-Convert to CSV & Download Offline Toggle
                        FilterChip(
                            selected = autoDownloadCsvOffline,
                            onClick = { autoDownloadCsvOffline = !autoDownloadCsvOffline },
                            label = {
                                Text("Auto-convert extracted contacts to CSV (.csv) & download offline")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (autoDownloadCsvOffline) Icons.Default.CheckCircle else Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Primary Action: Analyze All PDFs, Extract Contacts, Convert to CSV & Download Offline
                        Button(
                            onClick = {
                                viewModel.analyzeBulkDocumentsAndStoreInDba(
                                    context = context,
                                    autoDownloadCsv = autoDownloadCsvOffline
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("analyze_bulk_pdfs_csv_button"),
                            enabled = !isBulkExtracting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isBulkExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Converting ${selectedDocuments.size} PDFs to Excel...")
                            } else {
                                Icon(Icons.Default.TableChart, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Convert ${selectedDocuments.size} PDF(s) → Excel (.xlsx)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Progress Status indicator
                    if (isBulkExtracting) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bulkProgress,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (bulkProgressTotal > 0) {
                                        Text(
                                            text = "$bulkProgressCurrent / $bulkProgressTotal PDFs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                if (bulkProgressTotal > 0) {
                                    LinearProgressIndicator(
                                        progress = { bulkProgressCurrent.toFloat() / bulkProgressTotal.toFloat() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Batch Completed Feedback
                    if (bulkBatchCompleted && bulkExtractedLeads.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bulk PDF Extraction & CSV Ready!",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Extracted ${bulkExtractedLeads.size} contacts across ${selectedDocuments.size.coerceAtLeast(1)} PDF(s) and converted into CSV format.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                // 1-Tap Download Excel (.xlsx) Offline (Primary Format)
                                Button(
                                    onClick = {
                                        viewModel.downloadExcelOffline(context, bulkExtractedLeads, isCsv = false)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("download_bulk_excel_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41))
                                ) {
                                    Icon(
                                        Icons.Default.TableChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Download Excel Workbook (.xlsx) Offline",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 1-Tap Download CSV (.csv) Offline
                                Button(
                                    onClick = {
                                        viewModel.downloadExcelOffline(context, bulkExtractedLeads, isCsv = true)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("download_bulk_csv_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                                ) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Download Contacts CSV (.csv) Offline",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.clearBulkDocuments() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clear Screen (No Data Saved)")
                                }

                                // Live Converted CSV Format Preview
                                if (bulkCsvContent.isNotBlank()) {
                                    OutlinedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Converted CSV Output (${bulkExtractedLeads.size} contact rows)",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    AssistChip(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(
                                                                android.content.ClipData.newPlainText("Extracted Contacts CSV", bulkCsvContent)
                                                            )
                                                            viewModel.setBannerMessage("Copied CSV content to clipboard!")
                                                        },
                                                        label = { Text("Copy CSV", fontSize = 11.sp) }
                                                    )
                                                    AssistChip(
                                                        onClick = { showCsvPreview = !showCsvPreview },
                                                        label = { Text(if (showCsvPreview) "Hide" else "Show", fontSize = 11.sp) }
                                                    )
                                                }
                                            }

                                            AnimatedVisibility(visible = showCsvPreview) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(8.dp)
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                    Text(
                                                        text = bulkCsvContent.trim().lines().take(12).joinToString("\n"),
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            fontSize = 11.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Extracted Leads Quick Preview
                                Text(
                                    text = "Extracted Contact Details (${bulkExtractedLeads.size} contacts):",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    bulkExtractedLeads.take(8).forEach { lead ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = lead.name.firstOrNull()?.uppercase() ?: "L",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = lead.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${lead.jobTitle.ifBlank { "Contact" }} • ${lead.company}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${lead.email} | ${lead.phone}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1
                                                    )
                                                    if (lead.sourceDetail.isNotBlank()) {
                                                        Text(
                                                            text = "Source PDF: ${lead.sourceDetail}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                                SuggestionChip(
                                                    onClick = { },
                                                    label = { Text("${lead.confidence}%", fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }

                                    if (bulkExtractedLeads.size > 8) {
                                        Text(
                                            text = "+ ${bulkExtractedLeads.size - 8} more contacts included in CSV & DBA table",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Extraction Progress Indicator
        AnimatedVisibility(
            visible = isExtracting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = extractionStep.ifBlank { "Agent processing..." },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Live Extraction Result Review Card (Bulk or Single)
        if (!isExtracting) {
            if (cardBatchLeads.isNotEmpty()) {
                BulkCardsExtractionCard(
                    leads = cardBatchLeads,
                    onAddManualCard = {
                        showAddManualCardDialog = true
                    },
                    onAddCardFromCamera = {
                        onCameraClick(append = true)
                    },
                    onAddCardsFromGallery = {
                        launchPhotoPicker(append = true)
                    },
                    onRemoveCard = { lead ->
                        viewModel.removeCardFromBatch(lead)
                    },
                    onDownloadExcelOffline = {
                        viewModel.downloadExcelOffline(context, cardBatchLeads, isCsv = false)
                    },
                    onDownloadCsvOffline = {
                        viewModel.downloadExcelOffline(context, cardBatchLeads, isCsv = true)
                    },
                    onExportToExcel = {
                        val uri = viewModel.createExcelShareableUri(context, cardBatchLeads)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Extracted Business Cards (${cardBatchLeads.size})")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = android.content.ClipData.newRawUri("Cards Excel", uri)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Export Leads to Excel").apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(chooser)
                    },
                    onSyncToSheets = {
                        viewModel.syncCardBatchToSheets()
                    },
                    onOpenLeadsDba = {
                        viewModel.setTab(AppTab.LEADS)
                    },
                    onDiscard = {
                        viewModel.clearCardBatch()
                    }
                )
            } else if (lastResult != null && lastResult!!.success) {
                ExtractionResultCard(
                    result = lastResult!!,
                    autoSyncActive = agentConfig.autoSyncToSheets && agentConfig.googleSheetWebhookUrl.isNotBlank(),
                    onSaveToLocalDb = {
                        viewModel.saveCurrentResultToDbAndSync(syncNow = false)
                    },
                    onSaveAndSync = {
                        viewModel.saveCurrentResultToDbAndSync(syncNow = true)
                    },
                    onExportToExcel = {
                        val r = lastResult!!
                        val tempLead = ExtractedLead(
                            name = r.name,
                            company = r.company,
                            jobTitle = r.jobTitle,
                            email = r.email,
                            phone = r.phone,
                            website = r.website,
                            address = r.address,
                            notes = r.notes,
                            source = if (extractModeTab == 1) "CARD_SCAN" else "WEBSITE",
                            confidence = r.confidence
                        )
                        val uri = viewModel.createExcelShareableUri(context, listOf(tempLead))
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Lead: ${tempLead.name} - ${tempLead.company}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = android.content.ClipData.newRawUri("Lead Excel", uri)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Export Lead to Excel").apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(chooser)
                    },
                    onDownloadExcelOffline = {
                        val r = lastResult!!
                        val tempLead = ExtractedLead(
                            name = r.name,
                            company = r.company,
                            jobTitle = r.jobTitle,
                            email = r.email,
                            phone = r.phone,
                            website = r.website,
                            address = r.address,
                            notes = r.notes,
                            source = if (extractModeTab == 1) "CARD_SCAN" else "WEBSITE",
                            confidence = r.confidence
                        )
                        viewModel.downloadExcelOffline(context, listOf(tempLead), isCsv = false)
                    },
                    onDiscard = {
                        viewModel.clearLastExtraction()
                    }
                )
            }
        }
    }

    if (noCardRecognizedAlert != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoCardAlert() },
            modifier = Modifier.testTag("no_card_notification_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "No Card Notification",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "There is no card",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "There is no card recognized in the scanned photo. Please place 1 to 20 business cards clearly in frame and scan again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissNoCardAlert()
                    },
                    modifier = Modifier.testTag("no_card_dialog_ok_button")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissNoCardAlert()
                        onCameraClick(append = false)
                    }
                ) {
                    Text("Scan Again")
                }
            }
        )
    }

    if (showAddManualCardDialog) {
        AddManualCardDialog(
            nextCardNumber = cardBatchLeads.size + 1,
            onDismiss = { showAddManualCardDialog = false },
            onConfirm = { name, jobTitle, company, phone, email, website, address, notes ->
                viewModel.addManualCardToBatch(
                    name = name,
                    jobTitle = jobTitle,
                    company = company,
                    phone = phone,
                    email = email,
                    website = website,
                    address = address,
                    notes = notes
                )
                showAddManualCardDialog = false
            }
        )
    }
}

@Composable
fun AddManualCardDialog(
    nextCardNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        jobTitle: String,
        company: String,
        phone: String,
        email: String,
        website: String,
        address: String,
        notes: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Card #$nextCardNumber to Batch",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { jobTitle = it },
                    label = { Text("Job Title / Role") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Organization") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone / Mobile") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, jobTitle, company, phone, email, website, address, notes)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Card to Batch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkCardsExtractionCard(
    leads: List<ExtractedLead>,
    onAddManualCard: () -> Unit,
    onAddCardFromCamera: () -> Unit,
    onAddCardsFromGallery: () -> Unit,
    onRemoveCard: (ExtractedLead) -> Unit,
    onDownloadExcelOffline: () -> Unit,
    onDownloadCsvOffline: () -> Unit,
    onExportToExcel: () -> Unit,
    onSyncToSheets: () -> Unit,
    onOpenLeadsDba: () -> Unit,
    onDiscard: () -> Unit
) {
    var showAllCards by remember { mutableStateOf(false) }
    val displayedLeads = if (showAllCards || leads.size <= 15) leads else leads.take(15)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bulk_cards_extraction_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Extracted Cards (${leads.size} Cards)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Live in-memory preview • Zero data saved to device storage or database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${leads.size} Cards",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Prominent "+ Add One More Card" Bar inside the Batch Results Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Want to add more cards to this batch (${leads.size} → ${leads.size + 1}+)?",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onAddCardFromCamera,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_add_camera_card_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Capture Image", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = onAddCardsFromGallery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_add_gallery_card_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Add 1–50 Images", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onAddManualCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_add_manual_card_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Add 1 More Card Manually", fontSize = 12.sp)
                    }
                }
            }

            // Direct 1-Tap Offline Excel (.xlsx) & CSV (.csv) Download Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadExcelOffline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_bulk_cards_excel_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41))
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Excel (.xlsx)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onDownloadCsvOffline,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_bulk_cards_csv_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CSV (.csv)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // Secondary Actions Row: Share Excel or Clear Screen (Zero Data Saved)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onExportToExcel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Share / Open Excel (.xlsx)", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onDiscard,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear")
                }
            }

            // Excel Sheet Auto-Generated Status Banner + Live Excel Spreadsheet Table Preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF107C41).copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFF107C41),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Excel Table (${leads.size} Cards)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF107C41)
                        )
                        Text(
                            text = "All ${leads.size} extracted cards are shown in memory. No data is saved to storage or database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Excel Spreadsheet Table View (.xlsx Columns & Rows)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("excel_table_preview_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Excel Header Row
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF107C41))
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                        Text("Full Name", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(150.dp))
                        Text("Company", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(180.dp))
                        Text("Job Title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(140.dp))
                        Text("Phone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(185.dp))
                        Text("Email", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(180.dp))
                        Text("Website", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(160.dp))
                        Text("Address", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(260.dp))
                    }

                    // Excel Data Rows
                    leads.forEachIndexed { idx, item ->
                        val rowBg = if (idx % 2 == 0) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        Row(
                            modifier = Modifier
                                .background(rowBg)
                                .padding(vertical = 7.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                            Text(item.name, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(150.dp))
                            Text(item.company, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(180.dp))
                            Text(item.jobTitle, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(140.dp))
                            Text(item.phone, fontSize = 12.sp, maxLines = 2, modifier = Modifier.width(185.dp))
                            Text(item.email, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(180.dp))
                            Text(item.website, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(160.dp))
                            Text(item.address, fontSize = 12.sp, maxLines = 2, modifier = Modifier.width(260.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extracted Business Cards (${leads.size}):",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                if (leads.size > 15) {
                    TextButton(onClick = { showAllCards = !showAllCards }) {
                        Text(if (showAllCards) "Show First 15" else "Show All ${leads.size} Cards")
                    }
                }
            }

            // List of cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                displayedLeads.forEachIndexed { index, lead ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = lead.name.ifBlank { "Contact ${index + 1}" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (lead.jobTitle.isNotBlank() || lead.company.isNotBlank()) {
                                            Text(
                                                text = listOf(lead.jobTitle, lead.company).filter { it.isNotBlank() }.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${lead.confidence}%", fontSize = 11.sp) }
                                    )
                                    IconButton(
                                        onClick = { onRemoveCard(lead) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove card",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (lead.phone.isNotBlank()) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(lead.phone, fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                                if (lead.email.isNotBlank()) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(lead.email, fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                                if (lead.website.isNotBlank()) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(lead.website.take(24), fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }

                            if (lead.address.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = lead.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (!showAllCards && leads.size > 15) {
                    OutlinedButton(
                        onClick = { showAllCards = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("View All ${leads.size} Extracted Business Cards")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractionResultCard(
    result: ExtractionResult,
    autoSyncActive: Boolean,
    onSaveToLocalDb: () -> Unit,
    onSaveAndSync: () -> Unit,
    onDownloadExcelOffline: () -> Unit = {},
    onExportToExcel: () -> Unit = {},
    onDiscard: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("extraction_result_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Confidence Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Extracted Lead Intelligence",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${result.confidence}% Match",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Person / Entity Overview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.name.firstOrNull()?.uppercase() ?: "C",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column {
                    Text(
                        text = result.name.ifBlank { "Contact Person" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (result.jobTitle.isNotBlank() || result.company.isNotBlank()) {
                        Text(
                            text = listOf(result.jobTitle, result.company).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contact chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result.email.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(result.email) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (result.phone.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(result.phone) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (result.website.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(result.website.take(28)) },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            if (result.address.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.notes.isNotBlank()) {
                Text(
                    text = result.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }

            // Action Buttons: Direct Excel Download & Share (No Data Saved in Database)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadExcelOffline,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_single_excel_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF107C41)
                    )
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Download Excel", modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Download Excel (.xlsx)", color = Color.White)
                }

                FilledTonalButton(
                    onClick = onExportToExcel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share .xlsx")
                }

                OutlinedButton(
                    onClick = onDiscard,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear")
                }
            }
        }
    }
}
