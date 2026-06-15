package com.patrollink.presentation.screen

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout

class TextureMediaPlayerView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val textureView = TextureView(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var sourceUri: Uri? = null
    private var autoPlay: Boolean = true
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    var onBuffering: (() -> Unit)? = null
    var onReady: (() -> Unit)? = null
    var onPaused: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        textureView.surfaceTextureListener = this
        addView(
            textureView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    fun setMedia(uri: Uri, autoPlay: Boolean = true) {
        this.autoPlay = autoPlay
        if (sourceUri == uri && player != null) return
        sourceUri = uri
        preparePlayerIfReady()
    }

    fun play() {
        autoPlay = true
        val current = player
        if (current == null) {
            preparePlayerIfReady()
            return
        }
        runCatching {
            val duration = current.duration
            if (duration > 0 && current.currentPosition >= duration - 250) {
                current.seekTo(0)
            }
            current.start()
            onReady?.invoke()
        }
            .onFailure { onPlaybackError?.invoke("视频播放失败：${it.message ?: "播放器启动失败"}") }
    }

    fun pause() {
        autoPlay = false
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
            onPaused?.invoke()
        }
    }

    fun release() {
        releasePlayer()
        surface?.release()
        surface = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(surfaceTexture)
        preparePlayerIfReady()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        updateTextureTransform()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateTextureTransform()
    }

    private fun preparePlayerIfReady() {
        val uri = sourceUri ?: return
        val activeSurface = surface ?: return
        releasePlayer()
        videoWidth = 0
        videoHeight = 0
        onBuffering?.invoke()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.setSurface(activeSurface)
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                updateTextureTransform()
            }
            mediaPlayer.setOnPreparedListener {
                onReady?.invoke()
                if (autoPlay) {
                    runCatching { it.start() }
                        .onFailure { error -> onPlaybackError?.invoke("视频播放失败：${error.message ?: "播放器启动失败"}") }
                }
            }
            mediaPlayer.setOnCompletionListener {
                autoPlay = false
                onCompletion?.invoke()
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                autoPlay = false
                onPlaybackError?.invoke("视频播放失败：系统播放器无法解码或读取该文件（$what/$extra）")
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            releasePlayer()
            autoPlay = false
            onPlaybackError?.invoke("视频播放失败：${it.message ?: "文件不可读"}")
        }
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        runCatching {
            current.setOnPreparedListener(null)
            current.setOnCompletionListener(null)
            current.setOnErrorListener(null)
            current.setOnVideoSizeChangedListener(null)
            current.reset()
            current.release()
        }
    }

    private fun updateTextureTransform() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            textureView.setTransform(null)
            return
        }
        val viewRatio = width.toFloat() / height.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX: Float
        val scaleY: Float
        if (videoRatio > viewRatio) {
            scaleX = 1f
            scaleY = viewRatio / videoRatio
        } else {
            scaleX = videoRatio / viewRatio
            scaleY = 1f
        }
        textureView.setTransform(
            Matrix().apply {
                setScale(scaleX, scaleY, width / 2f, height / 2f)
            }
        )
    }
}
