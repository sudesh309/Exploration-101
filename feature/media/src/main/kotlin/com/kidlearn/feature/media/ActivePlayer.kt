package com.kidlearn.feature.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.kidlearn.domain.model.MediaRef
import java.io.File

/**
 * Keeps at most ONE media player alive in the whole app. Decoders are the most memory-hungry
 * thing on a low-end phone, so when a new audio/video block starts playing, the previous
 * player is released instead of paused. Players are created only when the learner taps play.
 */
@OptIn(UnstableApi::class)
internal object ActivePlayer {
    /** Identifies the block currently owning the player (observable from Compose). */
    var owner: Any? by mutableStateOf(null)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }
    }

    fun play(context: Context, newOwner: Any, media: MediaRef): ExoPlayer {
        val current = player
        if (current != null && owner === newOwner) {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            current.play()
            return current
        }
        releaseCurrent()
        val created = ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(lowMemoryLoadControl())
            .build()
            .apply {
                addListener(listener)
                setMediaItem(MediaItem.fromUri(media.toUri()))
                prepare()
                play()
            }
        player = created
        owner = newOwner
        return created
    }

    fun pause(requester: Any) {
        if (owner === requester) player?.pause()
    }

    fun playerFor(requester: Any): ExoPlayer? = if (owner === requester) player else null

    fun release(requester: Any) {
        if (owner === requester) releaseCurrent()
    }

    private fun releaseCurrent() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        owner = null
        isPlaying = false
    }

    /** Smaller buffers than the defaults (50s): plenty for local files, far less RAM. */
    private fun lowMemoryLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(5_000, 15_000, 1_000, 2_000)
        .build()

    private fun MediaRef.toUri(): Uri = if (isRemote) Uri.parse(location) else Uri.fromFile(File(location))
}
