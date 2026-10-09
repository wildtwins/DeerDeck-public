package com.wildtwins.deerdeck.ui.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.video.FileOutputOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.DeerDeckApplication
import com.wildtwins.deerdeck.data.settings.MediaSaveLocation
import com.wildtwins.deerdeck.ui.UiFormatters
import com.wildtwins.deerdeck.ui.theme.DeerDeckTheme
import java.io.File
import java.util.Locale

class CameraCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialMode = when (intent.getStringExtra(EXTRA_CAPTURE_MODE)) {
            MODE_VIDEO -> CaptureMode.Video
            else -> CaptureMode.Photo
        }
        val settingsRepository = (application as DeerDeckApplication).settingsRepository

        setContent {
            val themeMode = settingsRepository.themeMode.collectAsStateWithLifecycle()
            val mediaSaveLocation = settingsRepository.mediaSaveLocation.collectAsStateWithLifecycle()
            val hapticFeedbackEnabled = settingsRepository.hapticFeedbackEnabled.collectAsStateWithLifecycle()
            val savedMedia = remember { mutableStateListOf<CapturedMedia>() }

            fun finishCapture() {
                if (savedMedia.isEmpty()) {
                    setResult(Activity.RESULT_CANCELED)
                } else {
                    setResult(Activity.RESULT_OK, savedMedia.toResultIntent())
                }
                finish()
            }

            BackHandler(onBack = ::finishCapture)
            DeerDeckTheme(themeMode = themeMode.value) {
                CameraCaptureScreen(
                    initialMode = initialMode,
                    mediaSaveLocation = mediaSaveLocation.value,
                    hapticFeedbackEnabled = hapticFeedbackEnabled.value,
                    savedMediaCount = savedMedia.size,
                    onMediaSaved = { uri, type ->
                        savedMedia += CapturedMedia(uri, type)
                        setResult(Activity.RESULT_OK, savedMedia.toResultIntent())
                    },
                    onClose = ::finishCapture,
                )
            }
        }
    }

    companion object {
        const val EXTRA_CAPTURE_MODE = "com.wildtwins.deerdeck.EXTRA_CAPTURE_MODE"
        const val EXTRA_MEDIA_URI = "com.wildtwins.deerdeck.EXTRA_MEDIA_URI"
        const val EXTRA_MEDIA_TYPE = "com.wildtwins.deerdeck.EXTRA_MEDIA_TYPE"
        const val EXTRA_MEDIA_URIS = "com.wildtwins.deerdeck.EXTRA_MEDIA_URIS"
        const val EXTRA_MEDIA_TYPES = "com.wildtwins.deerdeck.EXTRA_MEDIA_TYPES"
        const val EXTRA_PHOTO_URI = "com.wildtwins.deerdeck.EXTRA_PHOTO_URI"
        const val MODE_PHOTO = "photo"
        const val MODE_VIDEO = "video"
    }
}

private enum class CaptureMode {
    Photo,
    Video,
}

private data class CapturedMedia(
    val uri: String,
    val type: String,
)

private fun List<CapturedMedia>.toResultIntent(): Intent {
    val latest = last()
    return Intent()
        .putStringArrayListExtra(
            CameraCaptureActivity.EXTRA_MEDIA_URIS,
            ArrayList(map { it.uri }),
        )
        .putStringArrayListExtra(
            CameraCaptureActivity.EXTRA_MEDIA_TYPES,
            ArrayList(map { it.type }),
        )
        .putExtra(CameraCaptureActivity.EXTRA_MEDIA_URI, latest.uri)
        .putExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPE, latest.type)
        .putExtra(CameraCaptureActivity.EXTRA_PHOTO_URI, latest.uri)
}

@Composable
private fun CameraCaptureScreen(
    initialMode: CaptureMode,
    mediaSaveLocation: MediaSaveLocation,
    hapticFeedbackEnabled: Boolean,
    savedMediaCount: Int,
    onMediaSaved: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var permissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (permissionGranted) {
        CameraPreview(
            initialMode = initialMode,
            mediaSaveLocation = mediaSaveLocation,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            savedMediaCount = savedMediaCount,
            onMediaSaved = onMediaSaved,
            onClose = onClose,
        )
    } else {
        PermissionMessage(onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }, onClose = onClose)
    }
}

@Composable
private fun CameraPreview(
    initialMode: CaptureMode,
    mediaSaveLocation: MediaSaveLocation,
    hapticFeedbackEnabled: Boolean,
    savedMediaCount: Int,
    onMediaSaved: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val previewView = remember { PreviewView(context) }
    var mode by remember { mutableStateOf(initialMode) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    val zoomState = camera?.cameraInfo?.zoomState?.value
    val minZoom = zoomState?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.maxZoomRatio ?: 1f

    fun hapticTick() {
        if (hapticFeedbackEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun setZoom(value: Float) {
        val clean = value.coerceIn(minZoom, maxZoom)
        zoomRatio = clean
        camera?.cameraControl?.setZoomRatio(clean)
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.HD),
                    ),
                )
                .build()
            val video = VideoCapture.withOutput(recorder)

            runCatching {
                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    video,
                )
                camera = boundCamera
                zoomRatio = boundCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                imageCapture = capture
                videoCapture = video
            }.onFailure {
                error = "Camera unavailable"
            }
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera, minZoom, maxZoom) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (maxZoom > minZoom) {
                            setZoom(zoomRatio * zoomChange)
                        }
                    }
                },
        )

        IconButton(
            onClick = {
                hapticTick()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
        }

        if (savedMediaCount > 0) {
            Text(
                text = "Saved $savedMediaCount",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) {
                Text(error!!, color = Color.White)
            }
            if (maxZoom > minZoom) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Zoom ${String.format(Locale.US, "%.1f", zoomRatio)}x",
                        color = Color.White,
                    )
                    Slider(
                        value = zoomRatio.coerceIn(minZoom, maxZoom),
                        onValueChange = { setZoom(it) },
                        valueRange = minZoom..maxZoom,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == CaptureMode.Photo) {
                    Button(onClick = { hapticTick(); mode = CaptureMode.Photo }, enabled = recording == null) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Photo")
                    }
                    OutlinedButton(onClick = { hapticTick(); mode = CaptureMode.Video }, enabled = recording == null) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Video")
                    }
                } else {
                    OutlinedButton(onClick = { hapticTick(); mode = CaptureMode.Photo }, enabled = recording == null) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Photo")
                    }
                    Button(onClick = { hapticTick(); mode = CaptureMode.Video }, enabled = recording == null) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Video")
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    hapticTick()
                    if (mode == CaptureMode.Photo) {
                        val capture = imageCapture ?: return@FloatingActionButton
                        val output = context.createPhotoOutput(mediaSaveLocation)
                        capture.takePicture(
                            output.options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val uri = outputFileResults.savedUri ?: output.expectedUri
                                    if (uri != null) {
                                        onMediaSaved(uri.toString(), CameraCaptureActivity.MODE_PHOTO)
                                        error = "Photo saved"
                                    } else {
                                        error = "Photo saved, but Android did not return a URI"
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    error = "Photo failed: ${exception.message ?: "unknown error"}"
                                }
                            },
                        )
                    } else {
                        val activeRecording = recording
                        if (activeRecording != null) {
                            activeRecording.stop()
                            return@FloatingActionButton
                        }

                        val capture = videoCapture ?: return@FloatingActionButton
                        val output = context.createVideoOutput(mediaSaveLocation)
                        val pendingRecording = output.mediaStoreOptions?.let { options ->
                            capture.output.prepareRecording(context, options)
                        } ?: capture.output.prepareRecording(context, output.fileOutputOptions!!)
                        recording = pendingRecording
                            .start(ContextCompat.getMainExecutor(context)) { event ->
                                when (event) {
                                    is VideoRecordEvent.Finalize -> {
                                        recording = null
                                        if (!event.hasError()) {
                                            val uri = event.outputResults.outputUri.takeUnless { it == Uri.EMPTY }
                                                ?: output.expectedUri
                                            if (uri != null) {
                                                onMediaSaved(uri.toString(), CameraCaptureActivity.MODE_VIDEO)
                                                error = "Video saved"
                                            } else {
                                                error = "Video saved, but Android did not return a URI"
                                            }
                                        } else {
                                            error = "Video failed: ${event.error}"
                                        }
                                    }
                                }
                            }
                    }
                },
                shape = CircleShape,
                containerColor = if (recording == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            ) {
                if (mode == CaptureMode.Video && recording != null) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop video")
                } else if (mode == CaptureMode.Video) {
                    Icon(Icons.Default.Videocam, contentDescription = "Record video")
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                }
            }
        }
    }
}

@Composable
private fun PermissionMessage(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission is needed to save Withrow DeerDeck photos.",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestPermission) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Allow camera")
            }
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

private data class PhotoOutput(
    val options: ImageCapture.OutputFileOptions,
    val expectedUri: Uri?,
)

private data class VideoOutput(
    val mediaStoreOptions: MediaStoreOutputOptions? = null,
    val fileOutputOptions: FileOutputOptions? = null,
    val expectedUri: Uri?,
)

private fun android.content.Context.createPhotoOutput(location: MediaSaveLocation): PhotoOutput {
    val fileName = "deerdeck_${UiFormatters.photoTimestamp(System.currentTimeMillis())}.jpg"
    return when (location) {
        MediaSaveLocation.CameraRoll -> {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DeerDeck")
                }
            }
            PhotoOutput(
                options = ImageCapture.OutputFileOptions.Builder(
                    contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ).build(),
                expectedUri = null,
            )
        }
        MediaSaveLocation.DeerDeckOnly -> {
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "DeerDeck") }
                    ?: File(filesDir, "DeerDeck/photos"),
                fileName,
            ).apply {
                parentFile?.mkdirs()
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            PhotoOutput(
                options = ImageCapture.OutputFileOptions.Builder(file).build(),
                expectedUri = uri,
            )
        }
    }
}

private fun android.content.Context.createVideoOutput(location: MediaSaveLocation): VideoOutput {
    val fileName = "deerdeck_${UiFormatters.photoTimestamp(System.currentTimeMillis())}.mp4"
    return when (location) {
        MediaSaveLocation.CameraRoll -> {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DeerDeck")
                }
            }
            VideoOutput(
                mediaStoreOptions = MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                )
                    .setContentValues(contentValues)
                    .build(),
                expectedUri = null,
            )
        }
        MediaSaveLocation.DeerDeckOnly -> {
            val file = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, "DeerDeck") }
                    ?: File(filesDir, "DeerDeck/videos"),
                fileName,
            ).apply {
                parentFile?.mkdirs()
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            VideoOutput(
                fileOutputOptions = FileOutputOptions.Builder(file).build(),
                expectedUri = uri,
            )
        }
    }
}
