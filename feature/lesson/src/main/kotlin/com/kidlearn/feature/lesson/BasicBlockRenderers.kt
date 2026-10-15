package com.kidlearn.feature.lesson

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kidlearn.domain.model.ContentBlock
import com.kidlearn.domain.model.ImageBlock
import com.kidlearn.domain.model.TextBlock
import com.kidlearn.domain.model.TextStyle
import com.kidlearn.ui.blocks.BlockRenderer
import com.kidlearn.ui.blocks.LessonSession
import com.kidlearn.ui.components.MediaImage

/** Renderers for the visual learning mode: text and pictures. */
fun visualBlockRenderers(): List<BlockRenderer> = listOf(TextBlockRenderer, ImageBlockRenderer)

private object TextBlockRenderer : BlockRenderer {
    override val type = TextBlock.TYPE

    @Composable
    override fun Render(block: ContentBlock, session: LessonSession) {
        val text = block as TextBlock
        when (text.style) {
            TextStyle.HEADING -> Text(text.text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            TextStyle.BODY -> Text(text.text, style = MaterialTheme.typography.bodyLarge)
            TextStyle.FUN_FACT -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("💡 ${text.text}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private object ImageBlockRenderer : BlockRenderer {
    override val type = ImageBlock.TYPE

    @Composable
    override fun Render(block: ContentBlock, session: LessonSession) {
        val image = block as ImageBlock
        Column(Modifier.fillMaxWidth()) {
            MediaImage(
                media = image.image,
                contentDescription = image.altText ?: image.caption,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(MaterialTheme.shapes.medium),
            )
            image.caption?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}
