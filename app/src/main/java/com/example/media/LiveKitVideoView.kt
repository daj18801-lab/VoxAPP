package com.example.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/**
 * Renders a LiveKit [VideoTrack] (local or remote) inside Jetpack Compose
 * using LiveKit's [SurfaceViewRenderer] with safe lifecycle cleanup.
 */
@Composable
fun LiveKitVideoView(
    room: Room,
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    val boundTrackHolder = remember { arrayOfNulls<VideoTrack>(1) }
    val rendererHolder = remember { arrayOfNulls<SurfaceViewRenderer>(1) }

    DisposableEffect(videoTrack) {
        val view = rendererHolder[0]
        val prev = boundTrackHolder[0]
        if (prev != null && view != null && prev !== videoTrack) {
            runCatching { prev.removeRenderer(view) }
            boundTrackHolder[0] = null
        }
        if (videoTrack != null && view != null && boundTrackHolder[0] !== videoTrack) {
            runCatching { videoTrack.addRenderer(view) }
            boundTrackHolder[0] = videoTrack
        }
        onDispose {
            val currentView = rendererHolder[0]
            val currentTrack = boundTrackHolder[0]
            if (currentTrack != null && currentView != null) {
                runCatching { currentTrack.removeRenderer(currentView) }
                boundTrackHolder[0] = null
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                runCatching {
                    room.initVideoRenderer(this)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                    setMirror(mirror)
                }
                rendererHolder[0] = this
                if (videoTrack != null) {
                    runCatching { videoTrack.addRenderer(this) }
                    boundTrackHolder[0] = videoTrack
                }
            }
        },
        update = { view ->
            runCatching { view.setMirror(mirror) }
            val prev = boundTrackHolder[0]
            if (prev !== videoTrack) {
                if (prev != null) {
                    runCatching { prev.removeRenderer(view) }
                }
                if (videoTrack != null) {
                    runCatching { videoTrack.addRenderer(view) }
                }
                boundTrackHolder[0] = videoTrack
            }
        },
        onRelease = { view ->
            val prev = boundTrackHolder[0]
            if (prev != null) {
                runCatching { prev.removeRenderer(view) }
                boundTrackHolder[0] = null
            }
            runCatching { view.release() }
            if (rendererHolder[0] === view) {
                rendererHolder[0] = null
            }
        }
    )
}
