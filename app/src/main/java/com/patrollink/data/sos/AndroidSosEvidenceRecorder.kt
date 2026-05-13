package com.patrollink.data.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import com.patrollink.domain.SosEvidenceRecorder
import com.patrollink.domain.SosRecording
import java.io.File

class AndroidSosEvidenceRecorder(
    private val context: Context
) : SosEvidenceRecorder {
    private val directory = File(context.filesDir, "sos_audio").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var active: SosRecording? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun start(sessionId: String): SosRecording {
        stop()
        val file = File(directory, "$sessionId.m4a")
        val startedAt = System.currentTimeMillis()
        val nextRecorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = nextRecorder
        return SosRecording(sessionId, file.absolutePath, startedAt, stoppedAt = null, sizeBytes = 0L)
            .also { active = it }
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    override suspend fun stop(): SosRecording? {
        val current = active ?: return null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        val file = File(current.filePath)
        val stopped = current.copy(stoppedAt = System.currentTimeMillis(), sizeBytes = file.length())
        active = null
        return stopped
    }
}
