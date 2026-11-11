package dev.gitdroid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalMotion
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * A glass surface.
 *
 * The page underneath is a soft vertical gradient; cards float above it with
 * a translucent fill, a hairline stroke and a soft shadow. Nothing here
 * blurs — a blur would cost real frame time on a mid-range phone, and the
 * gradient already gives the depth the effect is after.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    borderColor: Color? = null,
    background: Color? = null,
    contentPadding: PaddingValues? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalGitDroidColors.current
    val space = LocalSpace.current
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = motion.press(),
        label = "card-scale",
    )

    val fill = background ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    val stroke = borderColor ?: colors.rule

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape ?: MaterialTheme.shapes.medium)
            .background(fill)
            // A hairline top edge catches the light and gives the card a
            // physical edge, the way a sheet of paper catches a window.
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
            }
            .border(BorderStroke(1.dp, stroke), shape ?: MaterialTheme.shapes.medium)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (contentPadding != null) Modifier.padding(contentPadding) else Modifier,
            ),
    ) {
        content()
    }
}

/**
 * The page background.
 *
 * A barely-there vertical gradient in the surface range — enough to stop the
 * screen feeling like a flat sheet of colour, not enough to compete with the
 * content.
 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalGitDroidColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.pageTop,
                    1f to colors.pageBottom,
                ),
            ),
    ) {
        content()
    }
}

/** A hairline divider that follows the theme's rule colour. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = LocalGitDroidColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color ?: colors.rule),
    )
}

/** A small status dot. */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 8,
    pulsing: Boolean = false,
) {
    val motion = LocalMotion.current
    val alpha by animateFloatAsState(
        targetValue = if (pulsing) 0.55f else 1f,
        animationSpec = motion.standard(),
        label = "dot-alpha",
    )
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = alpha)),
    )
}

/** A labelled section header. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.gutter, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = type.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** An empty state: a glyph, a headline and an optional explanation. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.xxl)
            .padding(vertical = space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = colors.textTertiary,
        )
        Spacer(Modifier.height(space.md))
        Text(
            text = title,
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(space.xs))
            Text(
                text = message,
                style = type.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(space.lg))
            action()
        }
    }
}

/** An inline error banner. */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    val alpha by animateFloatAsState(
        targetValue = if (message.isBlank()) 0f else 1f,
        animationSpec = LocalMotion.current.standard(),
        label = "banner-alpha",
    )

    if (message.isBlank()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = space.gutter, vertical = space.xs),
        color = colors.dangerContainer.copy(alpha = alpha),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = space.md, vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onDangerContainer,
            )
            Spacer(Modifier.width(space.sm))
            Text(
                text = message,
                style = type.bodySmall,
                color = colors.onDangerContainer,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRetry != null) {
                Spacer(Modifier.width(space.sm))
                Text(
                    text = "Retry",
                    style = type.label,
                    color = colors.onDangerContainer,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
    }
}

/** A small pill used for labels and metadata. */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    background: Color? = null,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val fill = background ?: tint.copy(alpha = 0.12f)

    Text(
        text = text,
        style = type.labelSmall,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(horizontal = space.sm, vertical = 3.dp),
    )
}

/** A monospace code block background. */
@Composable
fun CodeWell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalGitDroidColors.current
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.codeWell)
            .border(BorderStroke(1.dp, colors.codeWellBorder), MaterialTheme.shapes.medium),
    ) {
        content()
    }
}
