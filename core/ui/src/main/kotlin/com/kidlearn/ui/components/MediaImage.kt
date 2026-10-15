package com.kidlearn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.kidlearn.domain.model.MediaRef
import java.io.File

/** Coil model for a [MediaRef]: local files are loaded from disk, remote ones by URL. */
fun MediaRef.imageModel(): Any = if (isRemote) location else File(location)

/**
 * Displays course media. Coil decodes the bitmap at the size the layout actually needs (never
 * the full resolution of the source file), which keeps memory use low on 3 GB phones.
 */
@Composable
fun MediaImage(
    media: MediaRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    AsyncImage(
        model = media.imageModel(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
