package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LoopingVideoPlayer(
    videoFile: File,
    shouldPlay: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    resetToStartWhenPausedOutOfView: Boolean = false,
    isActiveInViewport: Boolean = true,
    onFirstFrameRendered: () -> Unit = {},
    onPlaybackError: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val effectiveShouldPlay = shouldPlay && isLifecycleResumed
    val currentShouldPlay by rememberUpdatedState(effectiveShouldPlay)
    val currentIsMuted by rememberUpdatedState(isMuted)
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrameRendered)
    val currentOnError by rememberUpdatedState(onPlaybackError)

    var mediaPlayer by remember(videoFile.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(videoFile.absolutePath) { mutableStateOf(false) }
    var firstFrameDispatched by remember(videoFile.absolutePath) { mutableStateOf(false) }
    var currentSurface by remember(videoFile.absolutePath) { mutableStateOf<Surface?>(null) }
    var textureViewRef by remember(videoFile.absolutePath) { mutableStateOf<TextureView?>(null) }
    var videoWidth by remember(videoFile.absolutePath) { mutableIntStateOf(0) }
    var videoHeight by remember(videoFile.absolutePath) { mutableIntStateOf(0) }
    var wasActiveInViewport by remember(videoFile.absolutePath) { mutableStateOf(isActiveInViewport) }

    fun markFirstFrame() {
        if (!firstFrameDispatched) {
            firstFrameDispatched = true
            currentOnFirstFrame()
        }
    }

    fun refreshMatrix() {
        val tv = textureViewRef ?: return
        applyVideoAspectMatrix(
            textureView = tv,
            viewWidth = tv.width,
            viewHeight = tv.height,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
    }

    DisposableEffect(videoFile.absolutePath) {
        val player = MediaPlayer()
        var disposed = false

        runCatching {
            player.setDataSource(videoFile.absolutePath)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { _, width, height ->
                if (!disposed) {
                    videoWidth = width
                    videoHeight = height
                    refreshMatrix()
                }
            }
            player.setOnInfoListener { _, what, _ ->
                if (!disposed && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    markFirstFrame()
                }
                false
            }
            player.setOnErrorListener { _, _, _ ->
                if (!disposed) {
                    currentOnError()
                }
                true
            }
            player.setOnPreparedListener { mp ->
                if (disposed) return@setOnPreparedListener
                isPrepared = true
                val volume = if (currentIsMuted) 0f else 1f
                runCatching { mp.setVolume(volume, volume) }
                val surf = currentSurface
                if (surf != null && surf.isValid) {
                    runCatching { mp.setSurface(surf) }
                    if (currentShouldPlay) {
                        runCatching { mp.start() }
                    } else {
                        runCatching { mp.seekTo(1) }
                    }
                }
            }
            player.prepareAsync()
            mediaPlayer = player
        }.onFailure {
            runCatching { player.release() }
            currentOnError()
        }

        onDispose {
            disposed = true
            mediaPlayer = null
            isPrepared = false
            runCatching {
                player.setSurface(null)
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        }
    }

    LaunchedEffect(mediaPlayer, isPrepared, isMuted) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        val vol = if (isMuted) 0f else 1f
        runCatching { mp.setVolume(vol, vol) }
    }

    LaunchedEffect(mediaPlayer, isPrepared, currentSurface, effectiveShouldPlay, isActiveInViewport) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        val surf = currentSurface

        if (resetToStartWhenPausedOutOfView && wasActiveInViewport && !isActiveInViewport) {
            runCatching {
                if (mp.isPlaying) mp.pause()
                mp.seekTo(0)
            }
        }
        wasActiveInViewport = isActiveInViewport

        if (surf != null && surf.isValid) {
            runCatching { mp.setSurface(surf) }
            if (effectiveShouldPlay) {
                runCatching {
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                }
            } else {
                runCatching {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                }
            }
        } else {
            runCatching {
                if (mp.isPlaying) mp.pause()
            }
        }
    }

    // Fallback in case a software codec/emulator does not emit MEDIA_INFO_VIDEO_RENDERING_START
    LaunchedEffect(mediaPlayer, isPrepared, effectiveShouldPlay, firstFrameDispatched) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared || !effectiveShouldPlay || firstFrameDispatched) return@LaunchedEffect
        repeat(20) {
            delay(40L)
            val playing = runCatching { mp.isPlaying }.getOrDefault(false)
            val pos = runCatching { mp.currentPosition }.getOrDefault(0)
            if (playing && pos > 0) {
                markFirstFrame()
                return@LaunchedEffect
            }
        }
        if (runCatching { mp.isPlaying }.getOrDefault(false)) {
            markFirstFrame()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isOpaque = false
                textureViewRef = this
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val surface = Surface(surfaceTexture)
                        currentSurface = surface
                        applyVideoAspectMatrix(
                            textureView = this@apply,
                            viewWidth = width,
                            viewHeight = height,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight
                        )
                        val mp = mediaPlayer
                        if (mp != null && isPrepared) {
                            runCatching { mp.setSurface(surface) }
                            val vol = if (currentIsMuted) 0f else 1f
                            runCatching { mp.setVolume(vol, vol) }
                            if (currentShouldPlay) {
                                runCatching {
                                    if (!mp.isPlaying) mp.start()
                                }
                            } else {
                                runCatching { mp.seekTo(1) }
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        applyVideoAspectMatrix(
                            textureView = this@apply,
                            viewWidth = width,
                            viewHeight = height,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight
                        )
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        runCatching { mediaPlayer?.setSurface(null) }
                        val old = currentSurface
                        currentSurface = null
                        runCatching { old?.release() }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        if (currentShouldPlay) {
                            markFirstFrame()
                        }
                    }
                }
            }
        },
        update = { tv ->
            if (textureViewRef !== tv) {
                textureViewRef = tv
            }
            applyVideoAspectMatrix(
                textureView = tv,
                viewWidth = tv.width,
                viewHeight = tv.height,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )
        },
        onRelease = { tv ->
            tv.surfaceTextureListener = null
            if (textureViewRef === tv) {
                textureViewRef = null
            }
            val old = currentSurface
            currentSurface = null
            runCatching { old?.release() }
        },
        modifier = modifier
    )
}

private fun applyVideoAspectMatrix(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val scaleX = viewWidth.toFloat() / videoWidth.toFloat()
    val scaleY = viewHeight.toFloat() / videoHeight.toFloat()
    val scale = if (videoWidth > videoHeight) {
        minOf(scaleX, scaleY)
    } else {
        maxOf(scaleX, scaleY)
    }
    val scaledWidth = videoWidth * scale
    val scaledHeight = videoHeight * scale
    val dx = (viewWidth - scaledWidth) / 2f
    val dy = (viewHeight - scaledHeight) / 2f

    val matrix = Matrix().apply {
        setScale(scaledWidth / viewWidth, scaledHeight / viewHeight)
        postTranslate(dx, dy)
    }
    textureView.setTransform(matrix)
}
