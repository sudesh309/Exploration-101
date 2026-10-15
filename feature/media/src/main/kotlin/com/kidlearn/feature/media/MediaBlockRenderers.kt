package com.kidlearn.feature.media

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.kidlearn.domain.model.AudioBlock
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.MediaRef
import com.kidlearn.domain.model.VideoBlock
import com.kidlearn.ui.blocks.BlockRenderer
import com.kidlearn.ui.blocks.LessonSession
import com.kidlearn.ui.components.MediaImage
import kotlinx.coroutines.delay

/** Renderers for the audio and video learning modes. */
fun mediaBlockRenderers(): List<BlockRenderer> = listOf(AudioBlockRenderer, VideoBlockRenderer)

/** Releases the player when this block leaves the screen (e.g. scrolled away or lesson closed). */
@Composable
private fun rememberPlayerOwner(): Any {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { ActivePlayer.release(owner) } }
    return owner
}

private object AudioBlockRenderer : BlockRenderer {
    override val type = AudioBlock.TYPE

    @Composable
    override fun Render(block: ContentBlock, session: LessonSession) {
        val audio = block as AudioBlock
        val context = LocalContext.current
        val owner = rememberPlayerOwner()
        val active = ActivePlayer.owner === owner
        val playing = active && ActivePlayer.isPlaying
        var progress by remember { mutableFloatStateOf(0f) }
        var showTranscript by remember { mutableStateOf(false) }

        LaunchedEffect(playing) {
            while (playing) {
                val player = ActivePlayer.playerFor(owner) ?: break
                val duration = player.duration.takeIf { it > 0 } ?: 1L
                progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                delay(250)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledIconButton(
                        onClick = { if (playing) ActivePlayer.pause(owner) else ActivePlayer.play(context, owner, audio.audio) },
                        modifier = Modifier.size(64.dp),
                    ) {
                        if (playing) Text("❚❚", style = MaterialTheme.typography.titleLarge) else Icon(Icons.Filled.PlayArrow, "Play", Modifier.size(40.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("🎧 ${audio.title ?: "Listen"}", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { if (active) progress else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                audio.transcript?.let { transcript ->
                    TextButton(onClick = { showTranscript = !showTranscript }) { Text(if (showTranscript) "Hide words" else "Show words") }
                    if (showTranscript) Text(transcript, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private object VideoBlockRenderer : BlockRenderer {
    override val type = VideoBlock.TYPE

    @OptIn(UnstableApi::class)
    @Composable
    override fun Render(block: ContentBlock, session: LessonSession) {
        val video = block as VideoBlock
        val context = LocalContext.current
        val owner = rememberPlayerOwner()
        val active = ActivePlayer.owner === owner

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            video.title?.let { Text("🎬 $it", style = MaterialTheme.typography.titleMedium) }
            Box(
                Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.medium).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    // The heavy PlayerView exists only while this video is the active one.
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { setShowNextButton(false); setShowPreviousButton(false) } },
                        update = { it.player = ActivePlayer.playerFor(owner) },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    VideoPoster(video.poster) { ActivePlayer.play(context, owner, video.video) }
                }
            }
        }
    }

    @Composable
    private fun VideoPoster(poster: MediaRef?, onPlay: () -> Unit) {
        Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            if (poster != null) MediaImage(poster, null, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
    }
}
