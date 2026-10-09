package com.wildtwins.deerdeck.ui.audio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.DeerDeckApplication
import com.wildtwins.deerdeck.ui.theme.DeerDeckTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

class AudioCaptureActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = (application as DeerDeckApplication).settingsRepository

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle()
            val hapticFeedbackEnabled by settingsRepository.hapticFeedbackEnabled.collectAsStateWithLifecycle()
            DeerDeckTheme(themeMode = themeMode) {
                AudioCaptureScreen(
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    onStart = ::startRecording,
                    onSave = ::saveRecordingAndFinish,
                    onCancel = ::cancelRecordingAndFinish,
                )
            }
        }
    }

    override fun onDestroy() {
        if (recorder != null) {
            cancelRecording()
        }
        super.onDestroy()
    }

    private fun startRecording(): Result<Unit> = runCatching {
        check(recorder == null) { "A recording is already running." }
        val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.let { File(it, "DeerDeck") }
            ?: File(filesDir, "DeerDeck/audio")
        check(directory.exists() || directory.mkdirs()) { "Could not create audio storage." }
        val file = File(directory, "Withrow-DeerDeck-voice-${AudioNameFormatter.format(Instant.now())}.m4a")
        val newRecorder = createRecorder()
        try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(128_000)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            outputFile = file
        } catch (error: Throwable) {
            runCatching { newRecorder.release() }
            file.delete()
            throw error
        }
    }

    private fun saveRecordingAndFinish(): Result<Unit> {
        return finishRecording().map { uri ->
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_MEDIA_URI, uri.toString())
                    .putExtra(EXTRA_MEDIA_TYPE, MEDIA_TYPE_AUDIO)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            finish()
        }
    }

    private fun finishRecording(): Result<Uri> = runCatching {
        val activeRecorder = checkNotNull(recorder) { "No recording is running." }
        val file = checkNotNull(outputFile) { "No audio file is available." }
        recorder = null
        outputFile = null
        try {
            activeRecorder.stop()
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            runCatching { activeRecorder.release() }
        }
        check(file.length() > 0L) { "The recording was empty." }
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun cancelRecordingAndFinish() {
        cancelRecording()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun cancelRecording() {
        val activeRecorder = recorder
        recorder = null
        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }
        outputFile?.delete()
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
    }

    companion object {
        const val EXTRA_MEDIA_URI = "com.wildtwins.deerdeck.audio.EXTRA_MEDIA_URI"
        const val EXTRA_MEDIA_TYPE = "com.wildtwins.deerdeck.audio.EXTRA_MEDIA_TYPE"
        const val MEDIA_TYPE_AUDIO = "audio"

        private val AudioNameFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault())
    }
}

@Composable
private fun AudioCaptureScreen(
    hapticFeedbackEnabled: Boolean,
    onStart: () -> Result<Unit>,
    onSave: () -> Result<Unit>,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(view.context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) {
            errorMessage = "Microphone permission is required to record an audio note."
        }
    }

    fun hapticTick(kind: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        if (hapticFeedbackEnabled) {
            view.performHapticFeedback(kind)
        }
    }

    fun start() {
        hapticTick()
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        onStart().onSuccess {
            startedAt = System.currentTimeMillis()
            elapsedSeconds = 0L
            errorMessage = null
            isRecording = true
        }.onFailure { error ->
            errorMessage = error.message ?: "Audio recording could not start."
        }
    }

    fun save() {
        hapticTick(HapticFeedbackConstants.LONG_PRESS)
        isRecording = false
        onSave().onFailure { error ->
            errorMessage = error.message ?: "Audio recording could not be saved."
        }
    }

    BackHandler {
        if (isRecording) save() else onCancel()
    }

    LaunchedEffect(isRecording, startedAt) {
        while (isRecording) {
            elapsedSeconds = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1_000L
            delay(250L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Session audio note", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = if (isRecording) {
                    "Recording ${formatDuration(elapsedSeconds)}"
                } else {
                    "Record what you hear or a quick field note."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            FloatingActionButton(
                onClick = { if (isRecording) save() else start() },
                modifier = Modifier.size(88.dp),
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop and save audio" else "Start audio recording",
                    modifier = Modifier.size(40.dp),
                )
            }
            errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Allow microphone")
                }
            }
            Spacer(Modifier.size(4.dp))
            OutlinedButton(onClick = onCancel, enabled = !isRecording) {
                Text("Cancel")
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
