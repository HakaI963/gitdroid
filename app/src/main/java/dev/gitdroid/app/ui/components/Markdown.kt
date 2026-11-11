package dev.gitdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/** The block types the renderer understands. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String, val depth: Int) : MarkdownBlock
    data class Ordered(val text: String, val number: Int) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Rule(val unused: Unit = Unit) : MarkdownBlock
}

/**
 * Renders Markdown the way github.com does on a phone: headings sized down a
 * step, code in a recessed well, lists indented, and inline runs styled
 * rather than stripped.
 *
 * A deliberately small subset — headings, paragraphs, lists, fenced code,
 * quotes, rules and inline emphasis. Full CommonMark is not the point; a
 * readable README in a couple of hundred lines is.
 */
@Composable
fun MarkdownBody(
    content: String,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val blocks = androidx.compose.runtime.remember(content) { parseMarkdown(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Heading(block)
                is MarkdownBlock.Paragraph -> BodyParagraph(block.text)
                is MarkdownBlock.Bullet -> BulletLine(block)
                is MarkdownBlock.Ordered -> OrderedLine(block)
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Quote -> QuoteLine(block.text)
                is MarkdownBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LocalGitDroidColors.current.rule),
                )
            }
        }
    }
}

@Composable
private fun Heading(block: MarkdownBlock.Heading) {
    val type = LocalType.current
    val space = LocalSpace.current
    val style = when (block.level) {
        1 -> type.headline
        2 -> type.title
        3 -> type.titleSmall
        else -> type.label
    }
    Text(
        text = inlineSpans(block.text),
        style = style.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = if (block.level <= 2) space.md else 0.dp),
    )
}

@Composable
private fun BodyParagraph(text: String) {
    val type = LocalType.current
    SelectionContainer {
        Text(
            text = inlineSpans(text),
            style = type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletLine(block: MarkdownBlock.Bullet) {
    val type = LocalType.current
    val space = LocalSpace.current
    Row(Modifier.padding(start = (block.depth * 16).dp)) {
        Text(
            text = if (block.depth > 0) "◦" else "•",
            style = type.body,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(space.sm))
        SelectionContainer {
            Text(
                text = inlineSpans(block.text),
                style = type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OrderedLine(block: MarkdownBlock.Ordered) {
    val type = LocalType.current
    val space = LocalSpace.current
    val colors = LocalGitDroidColors.current
    Row {
        Text(
            text = "${block.number}.",
            style = type.body.copy(color = colors.textTertiary),
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(space.xs))
        SelectionContainer {
            Text(
                text = inlineSpans(block.text),
                style = type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val type = LocalType.current
    val space = LocalSpace.current
    CodeWell(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(space.md)) {
            if (block.language.isNotBlank()) {
                Text(
                    text = block.language.lowercase(),
                    style = type.labelSmall,
                    color = LocalGitDroidColors.current.textTertiary,
                    modifier = Modifier.padding(bottom = space.xs),
                )
            }
            SelectionContainer {
                Text(
                    text = block.text,
                    style = type.code,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun QuoteLine(text: String) {
    val type = LocalType.current
    val space = LocalSpace.current
    Row {
        Box(
            Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(space.md))
        SelectionContainer {
            Text(
                text = inlineSpans(text),
                style = type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Turns inline Markdown into styled text runs.
 *
 * Bold, italic, code and strikethrough are kept as *styling* rather than
 * being stripped, so a README reads the way it was written. Links keep their
 * label in the accent colour — the URL itself is noise on a phone screen.
 */
fun inlineSpans(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        val rest = source.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = rest.indexOf("**", 2)
                if (end > 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(rest.substring(2, end))
                    }
                    i += end + 2
                } else {
                    append(rest[0]); i++
                }
            }

            rest.startsWith("__") -> {
                val end = rest.indexOf("__", 2)
                if (end > 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(rest.substring(2, end))
                    }
                    i += end + 2
                } else {
                    append(rest[0]); i++
                }
            }

            rest.startsWith("`") -> {
                val end = rest.indexOf('`', 1)
                if (end > 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = LocalCodeHighlightBackground,
                        ),
                    ) {
                        append(rest.substring(1, end))
                    }
                    i += end + 1
                } else {
                    append(rest[0]); i++
                }
            }

            rest.startsWith("~~") -> {
                val end = rest.indexOf("~~", 2)
                if (end > 2) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(rest.substring(2, end))
                    }
                    i += end + 2
                } else {
                    append(rest[0]); i++
                }
            }

            // [label](url) — keep the label, drop the url.
            rest.startsWith("[") -> {
                val close = rest.indexOf(']')
                val openParen = close.takeIf { it > 0 }?.let { rest.indexOf('(', it) }
                val endParen = openParen?.let { rest.indexOf(')', it) }
                if (close > 0 && openParen != null && endParen != null) {
                    append(rest.substring(1, close))
                    i += endParen + 1
                } else {
                    append(rest[0]); i++
                }
            }

            rest.startsWith("*") && !rest.startsWith("**") -> {
                val end = rest.indexOf('*', 1)
                if (end > 1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(rest.substring(1, end))
                    }
                    i += end + 1
                } else {
                    append(rest[0]); i++
                }
            }

            else -> {
                append(rest[0])
                i++
            }
        }
    }
}

/** Stand-in background for inline code; the theme supplies the real value. */
internal val LocalCodeHighlightBackground = androidx.compose.ui.graphics.Color(0x14808080)

/**
 * A small Markdown block parser.
 *
 * Fenced code, ATX headings, setext-free, ordered and unordered lists with
 * one level of nesting, blockquotes and thematic breaks. Anything else is a
 * paragraph.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = source.replace("\r\n", "\n").lines()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                val fence = trimmed.take(3)
                val language = trimmed.drop(3).trim()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    body += lines[i]
                    i++
                }
                i++ // the closing fence
                blocks += MarkdownBlock.Code(language, body.joinToString("\n"))
            }

            trimmed.isEmpty() -> i++

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val text = trimmed.drop(level).trim().removeSuffix("#").trim()
                if (text.isNotEmpty()) blocks += MarkdownBlock.Heading(level, text)
                i++
            }

            trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                trimmed.count { it == '-' } == trimmed.length && trimmed.length >= 3 -> {
                blocks += MarkdownBlock.Rule()
                i++
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                blocks += MarkdownBlock.Quote(quote.joinToString(" "))
            }

            ORDERED.matches(trimmed) -> {
                val number = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                val text = trimmed.dropWhile { it.isDigit() }.removePrefix(".").removePrefix(")").trim()
                blocks += MarkdownBlock.Ordered(text, number)
                i++
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                val depth = (raw.length - raw.trimStart().length) / 2
                blocks += MarkdownBlock.Bullet(trimmed.drop(2), depth)
                i++
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (i < lines.size) {
                    val next = lines[i].trim()
                    if (next.isEmpty() ||
                        next.startsWith("#") ||
                        next.startsWith("```") ||
                        next.startsWith(">") ||
                        next.startsWith("- ") ||
                        next.startsWith("* ") ||
                        ORDERED.matches(next)
                    ) {
                        break
                    }
                    paragraph += next
                    i++
                }
                if (paragraph.isNotEmpty()) {
                    blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
                }
            }
        }
    }
    return blocks
}

private val ORDERED = Regex("""^\d+[.)]\s+.*""")
