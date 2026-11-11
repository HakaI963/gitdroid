package dev.gitdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.gitdroid.app.ui.theme.LocalType

/**
 * A circular avatar backed by a remote image.
 *
 * Falls back to the initial on a coloured disc, which is what github.com does
 * while an avatar loads and what every app does when the network is not there.
 */
@Composable
fun AsyncAvatar(
    url: String,
    fallback: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val type = LocalType.current

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {
                    Text(
                        text = fallback.take(1).uppercase(),
                        style = type.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                },
                error = {
                    Text(
                        text = fallback.take(1).uppercase(),
                        style = type.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallback.take(1).uppercase(),
                style = type.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
