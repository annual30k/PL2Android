package com.patrollink.presentation.screen

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView

class TextureMediaPlayerView(context: Context) : FrameLayout(context) {
    private val videoView = VideoView(context)
    private val mediaController = MediaController(context)
    private var sourceUri: Uri? = null
    private var autoPlay: Boolean = true
    private var prepared: Boolean = false

    var onBuffering: (() -> Unit)? = null
    var onReady: (() -> Unit)? = null
    var onPaused: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        addView(
            videoView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    fun setMedia(uri: Uri, autoPlay: Boolean = true) {
        this.autoPlay = autoPlay
        if (sourceUri == uri && prepared) return
        sourceUri = uri
        prepared = false
        onBuffering?.invoke()
        runCatching {
            videoView.stopPlayback()
            videoView.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            videoView.setOnPreparedListener { player ->
                prepared = true
                player.isLooping = false
                onReady?.invoke()
                if (this.autoPlay) {
                    videoView.start()
                    mediaController.show()
                }
            }
            videoView.setOnCompletionListener {
                this.autoPlay = false
                onCompletion?.invoke()
                mediaController.show()
            }
            videoView.setOnErrorListener { _, what, extra ->
                this.autoPlay = false
                prepared = false
                onPlaybackError?.invoke("视频播放失败：系统播放器无法解码或读取该文件（$what/$extra）")
                true
            }
            videoView.setVideoURI(uri)
            videoView.requestFocus()
        }.onFailure {
            prepared = false
            this.autoPlay = false
            onPlaybackError?.invoke("视频播放失败：${it.message ?: "文件不可读"}")
        }
    }

    fun play() {
        autoPlay = true
        val uri = sourceUri
        if (uri == null) {
            onPlaybackError?.invoke("视频播放失败：文件地址为空")
            return
        }
        if (!prepared) {
            setMedia(uri, autoPlay = true)
            return
        }
        runCatching {
            val duration = videoView.duration
            if (duration > 0 && videoView.currentPosition >= duration - 250) {
                videoView.seekTo(0)
            }
            videoView.start()
            mediaController.show()
            onReady?.invoke()
        }.onFailure {
            onPlaybackError?.invoke("视频播放失败：${it.message ?: "播放器启动失败"}")
        }
    }

    fun pause() {
        autoPlay = false
        runCatching {
            if (videoView.isPlaying) videoView.pause()
            mediaController.show()
            onPaused?.invoke()
        }
    }

    fun seekTo(positionMillis: Int) {
        runCatching {
            videoView.seekTo(positionMillis.coerceIn(0, videoView.duration.coerceAtLeast(0)))
            mediaController.show()
        }
    }

    fun currentPositionMillis(): Int =
        runCatching { videoView.currentPosition }.getOrDefault(0).coerceAtLeast(0)

    fun durationMillis(): Int =
        runCatching { videoView.duration }.getOrDefault(0).coerceAtLeast(0)

    fun release() {
        runCatching {
            mediaController.hide()
            videoView.stopPlayback()
        }
        prepared = false
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
