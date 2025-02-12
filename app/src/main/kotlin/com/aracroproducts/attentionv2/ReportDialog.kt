package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aracroproducts.attentionv2.ReportViewModel.Companion.UriState
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.getValue
import kotlin.math.min

class ReportDialog : AppCompatActivity() {

    private fun addUris(uris: List<Uri>) {
        // move processing off the main thread
        lifecycleScope.launch(Dispatchers.Default) {
            uris.forEach { uri ->
                // lazily start the job so it will not run until it has been explicitly started (prevents race condition with bitmap pair in the map)

                synchronized(reportModel.attachmentsThumbnails) {
                    // if the URI is a duplicate (like from a previous photo-picker session), increment reference count and skip
                    if (reportModel.attachmentsThumbnails[uri]?.referenceCount?.incrementAndGet() != null) {
                        return@forEach
                    }

                    val job = launch(Dispatchers.IO, CoroutineStart.LAZY) {
                        val mimeType =
                            if (uri.scheme == "content") contentResolver.getType(uri) else MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(uri.lastPathSegment?.substringAfter("."))
                        val (bitmap, type) = try {
                            when {
                                mimeType?.startsWith("image") == true -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        if (uri.scheme == "content") {
                                            Pair(
                                                applicationContext.contentResolver.loadThumbnail(
                                                    uri,
                                                    THUMBNAIL_SIZE,
                                                    null
                                                ), ReportViewModel.AttachmentType.IMAGE
                                            )
                                        } else if (uri.scheme == "file") {
                                            Pair(
                                                ThumbnailUtils.createImageThumbnail(
                                                    uri.toFile(),
                                                    THUMBNAIL_SIZE,
                                                    null
                                                ), ReportViewModel.AttachmentType.IMAGE
                                            )
                                        } else {
                                            Pair(
                                                DEFAULT_BITMAP,
                                                ReportViewModel.AttachmentType.IMAGE
                                            )
                                        }
                                    } else {
                                        val boundsStream =
                                            contentResolver.openInputStream(uri)
                                        val options = BitmapFactory.Options()
                                        options.inJustDecodeBounds = true
                                        BitmapFactory.decodeStream(boundsStream, null, options)
                                        boundsStream?.close()
                                        if (options.outHeight != 0) {
                                            // we've got bounds
                                            val widthSample =
                                                options.outWidth / THUMBNAIL_SIZE.width
                                            val heightSample =
                                                options.outHeight / THUMBNAIL_SIZE.height
                                            val sample = min(widthSample, heightSample)
                                            if (sample > 1) {
                                                options.inSampleSize = sample
                                            }
                                            options.inJustDecodeBounds = false
                                            yield()
                                            val decodeStream =
                                                contentResolver.openInputStream(uri)
                                            val bitmap =
                                                BitmapFactory.decodeStream(
                                                    decodeStream,
                                                    null,
                                                    options
                                                )
                                            decodeStream?.close()
                                            Pair(
                                                bitmap ?: DEFAULT_BITMAP,
                                                ReportViewModel.AttachmentType.IMAGE
                                            )
                                        } else {
                                            Pair(
                                                DEFAULT_BITMAP,
                                                ReportViewModel.AttachmentType.IMAGE
                                            )
                                        }
                                    }
                                }

                                mimeType?.startsWith("video") == true -> {
                                    val mediaMetadataRetriever = MediaMetadataRetriever()
                                    yield()
                                    mediaMetadataRetriever.setDataSource(
                                        applicationContext,
                                        uri
                                    )
                                    val thumbnailBytes = mediaMetadataRetriever.embeddedPicture

                                    yield()
                                    val unscaledBitmap = thumbnailBytes?.let {
                                        BitmapFactory.decodeByteArray(
                                            thumbnailBytes,
                                            0,
                                            thumbnailBytes.size
                                        )
                                    } ?: mediaMetadataRetriever.frameAtTime ?: DEFAULT_BITMAP

                                    val aspect =
                                        unscaledBitmap.height.toFloat() / unscaledBitmap.width
                                    val newSize = if (aspect > 1) {
                                        Size(
                                            (THUMBNAIL_SIZE.width / aspect).toInt(),
                                            THUMBNAIL_SIZE.height
                                        )
                                    } else {
                                        Size(
                                            THUMBNAIL_SIZE.width,
                                            (THUMBNAIL_SIZE.height * aspect).toInt()
                                        )
                                    }

                                    yield()
                                    Pair(
                                        Bitmap.createScaledBitmap(
                                            unscaledBitmap,
                                            newSize.width,
                                            newSize.height,
                                            true
                                        ), ReportViewModel.AttachmentType.VIDEO
                                    )
                                }

                                else -> Pair(DEFAULT_BITMAP, ReportViewModel.AttachmentType.UNKNOWN)
                            }
                        } catch (e: Throwable) {
                            when (e) {
                                // exceptions thrown due to bad URIs being passed in
                                is IllegalArgumentException, is SecurityException, is IOException -> Pair(
                                    DEFAULT_BITMAP,
                                    ReportViewModel.AttachmentType.UNKNOWN
                                )

                                else -> throw e  // we must at least rethrow cancellation exceptions
                            }
                        }
                        yield()
                        synchronized(reportModel.attachmentsThumbnails) {
                            val saved = reportModel.attachmentsThumbnails[uri]
                            if (saved == null || saved.bitmap != null /* this really shouldn't be possible */) {
                                return@launch
                            } else {
                                reportModel.attachmentsThumbnails[uri] =
                                    UriState(bitmap, null, saved.referenceCount, type)
                            }
                        }
                    }

                    // save the job for potential future cancellation (it has not started at this point)
                    reportModel.attachmentsThumbnails[uri] = UriState(
                        null,
                        job,
                        AtomicInteger(1),
                        ReportViewModel.AttachmentType.UNKNOWN
                    )

                    // start the job after adding an entry to the map
                    job.start()
                }

            }

            // the thumbnail map must already be populated
            synchronized(reportModel.attachments) {
                reportModel.attachments.addAll(uris)
            }
        }
    }

    val reportModel: ReportViewModel by viewModels(factoryProducer = {
        ReportViewModelFactory(
            (application as AttentionApplication).container.repository,
            (application as AttentionApplication).container.settingsRepository,
            application,
            intent.getStringExtra(EXTRA_REPORT_MESSAGE) ?: ""
        )
    })

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> // Callback is invoked after the user selects a media item or closes the
        // photo picker.

        addUris(uris)
    }

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState - Data saved from before a configuration changed. Not used
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val photo = intent.getStringExtra(EXTRA_ATTACHMENT)

        if (photo != null) {
            addUris(listOf(Uri.parse(photo)))
        }

        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme {
                    ReportScreen()
                }
            } else {
                AppTheme {
                    ReportScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReportScreen() {

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(reportModel.snackbarMessage) {
            val message = reportModel.snackbarMessage
            if (message != null) {
                scope.launch {
                    when (snackbarHostState.showSnackbar(message = message)) {
                        SnackbarResult.Dismissed -> {
                            reportModel.snackbarMessage = null
                        }

                        else -> {}
                    }
                }
            }
        }
        Scaffold(topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ), title = {
                Text(getString(R.string.report))
            }, navigationIcon = {
                IconButton(onClick = {
                    onBackPressedDispatcher.onBackPressed()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, getString(
                            R.string.back
                        )
                    )
                }
            })
        }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            AnimatedContent(reportModel.showSuccessScreen, transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }, label = "reportScreen") { target ->
                when (target) {
                    false -> Dialog(padding)
                    true -> SuccessScreen(padding)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    private fun Dialog(padding: PaddingValues) {
        var reasonsExpanded by remember { mutableStateOf(false) }
        var enabled by remember { mutableStateOf(true) }
        var reasonError by remember { mutableStateOf(false) }
        var bodyError by remember { mutableStateOf(false) }


        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = reasonsExpanded && enabled,
                onExpandedChange = {
                    reasonsExpanded = it && enabled
                }) {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    enabled = enabled,
                    value = reportModel.reason?.description?.let { getString(it) } ?: "",
                    onValueChange = {},
                    isError = reasonError,
                    label = { Text(getString(R.string.report_reason_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonsExpanded && enabled) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = reasonsExpanded && enabled,
                    onDismissRequest = { reasonsExpanded = false }) {
                    ReportViewModel.Reason.entries.forEach { option ->
                        DropdownMenuItem(onClick = {
                            reportModel.reason = option
                            reasonError = false
                            reasonsExpanded = false
                        }, text = {
                            Text(text = getString(option.description))
                        })
                    }
                }
            }
            OutlinedTextField(
                value = reportModel.body,
                enabled = enabled,
                isError = bodyError,
                onValueChange = {
                    reportModel.body = it
                    if (it.isNotBlank()) bodyError = false
                },
                label = { Text(getString(R.string.report_body_label)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                minLines = 3
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reportModel.attachments.forEachIndexed { index, uri ->
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            reportModel.attachmentsThumbnails[uri]?.bitmap,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "thumbnailLoading"
                        ) { target ->
                            if (target != null) {
                                Image(
                                    bitmap = target.asImageBitmap(),
                                    contentDescription = getString(
                                        R.string.report_thumnbail_alt_text,
                                        uri.lastPathSegment
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }

                        val type = reportModel.attachmentsThumbnails[uri]?.type
                        if (type != null) {
                            Icon(
                                when (type) {
                                    ReportViewModel.AttachmentType.IMAGE -> Icons.Default.Image
                                    ReportViewModel.AttachmentType.VIDEO -> Icons.Default.Videocam
                                    ReportViewModel.AttachmentType.UNKNOWN -> Icons.Default.QuestionMark
                                },
                                null,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(16.dp, 16.dp)
                            )
                        }
                        IconButton(
                            {
                                synchronized(reportModel.attachments) {
                                    reportModel.attachments.removeAt(index)
                                }
                                synchronized(reportModel.attachmentsThumbnails) {
                                    reportModel.attachmentsThumbnails[uri]?.let {
                                        val references = it.referenceCount.decrementAndGet()
                                        if (references == 0) {
                                            it.job?.cancel()
                                            reportModel.attachmentsThumbnails.remove(uri)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset((20).dp, (-20).dp),
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                getString(R.string.delete),
                                modifier = Modifier
                                    .size(16.dp, 16.dp)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.size(64.dp)) {
                    IconButton(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                Dp.Hairline, MaterialTheme.colorScheme.onSurfaceVariant,
                                RoundedCornerShape(8.dp)
                            ), onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        }, enabled = enabled
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = getString(R.string.report_add_attachment_alt_text),
                            modifier = Modifier.fillMaxSize(0.5f)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton({ finish() }, enabled = enabled) {
                    Text(
                        text = getString(
                            android.R.string.cancel
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val submitText = @Composable { Text(text = getString(R.string.submit)) }
                MeasureView(submitText) { width, height ->
                    Button({
                        enabled = false
                        val reason = reportModel.reason
                        val body = reportModel.body
                        val attachments = reportModel.attachments
                        val validationErrors = ArrayList<String>()

                        if (reason == null) {
                            validationErrors.add(getString(R.string.report_reason_label))
                            reasonError = true
                        }
                        if (body.isBlank()) {
                            validationErrors.add(getString(R.string.report_body_label))
                            bodyError = true
                        }

                        if (reason == null || body.isBlank()) {
                            enabled = true
                            reportModel.snackbarMessage = getString(
                                R.string.report_validation_error,
                                validationErrors.joinToString()
                            )
                            return@Button
                        }

                        lifecycleScope.launch(Dispatchers.IO) {
                            val submitResult = reportModel.submit(reason, body, attachments)
                            enabled = true
                            when (submitResult) {
                                403 -> startActivity(
                                    Intent(
                                        this@ReportDialog,
                                        LoginActivity::class.java
                                    )
                                )

                                200 -> reportModel.showSuccessScreen = true
                                else -> reportModel.snackbarMessage = getString(R.string.send_error)
                            }
                        }
                    }, enabled = enabled) {
                        Box(
                            modifier = Modifier.size(width, height),
                            contentAlignment = Alignment.Center
                        ) {
                            if (enabled) submitText() else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(
                                        min(
                                            width,
                                            height
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }

        }
    }

    @Composable
    private fun SuccessScreen(padding: PaddingValues) {

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .aspectRatio(1f),
                contentDescription = null,
                tint = Color(0f, 0.53f, 0f)
            )
            Text(
                text = AnnotatedString.fromHtml(getString(R.string.report_success_message)),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
            Button({ finish() }) {
                Text(text = getString(R.string.done))
            }
        }
    }

    inner class ReportViewModelFactory(
        private val attentionRepository: AttentionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val application: Application,
        private val body: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                return ReportViewModel(
                    attentionRepository, preferencesRepository, application, body
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        val THUMBNAIL_SIZE: Size = Size(128, 128)
        val DEFAULT_BITMAP = Bitmap.createBitmap(
            THUMBNAIL_SIZE.width,
            THUMBNAIL_SIZE.height,
            Bitmap.Config.ARGB_8888
        )

        const val EXTRA_REPORT_MESSAGE = "com.aracroproducts.attention.extra.REPORT_MESSAGE"
        const val EXTRA_ATTACHMENT = "com.aracroproducts.attention.extra.ATTACHMENT"
    }
}