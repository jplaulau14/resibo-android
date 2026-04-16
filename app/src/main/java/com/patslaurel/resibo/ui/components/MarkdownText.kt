package com.patslaurel.resibo.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Renders a subset of Markdown as Compose [AnnotatedString].
 *
 * Supported:
 *   - `**bold**`
 *   - `*italic*` / `_italic_`
 *   - `~~strikethrough~~`
 *   - `- bullet` / `* bullet` (rendered as "  •  text")
 *   - `# Heading` through `### Heading` (bold, scaled by level)
 *
 * Not supported (intentionally — keep it simple for hackathon):
 *   - Links, images, code blocks, tables, nested lists
 *
 * If the model output contains unsupported syntax it passes through as plain text,
 * which is always safe.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val annotated =
        remember(markdown) {
            parseMarkdown(
                markdown,
                boldStyle = SpanStyle(fontWeight = FontWeight.Bold),
                italicStyle = SpanStyle(fontStyle = FontStyle.Italic),
                strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
                h1Style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = typography.titleLarge.fontSize),
                h2Style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = typography.titleMedium.fontSize),
                h3Style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = typography.titleSmall.fontSize),
            )
        }

    Text(
        text = annotated,
        style = typography.bodyMedium,
        modifier = modifier,
    )
}

private fun parseMarkdown(
    raw: String,
    boldStyle: SpanStyle,
    italicStyle: SpanStyle,
    strikethroughStyle: SpanStyle,
    h1Style: SpanStyle,
    h2Style: SpanStyle,
    h3Style: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        val lines = raw.lines()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") -> {
                    withStyle(h3Style) { append(trimmed.removePrefix("### ").trim()) }
                }

                trimmed.startsWith("## ") -> {
                    withStyle(h2Style) { append(trimmed.removePrefix("## ").trim()) }
                }

                trimmed.startsWith("# ") -> {
                    withStyle(h1Style) { append(trimmed.removePrefix("# ").trim()) }
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("  \u2022  ")
                    appendInlineMarkdown(trimmed.drop(2).trim(), boldStyle, italicStyle, strikethroughStyle)
                }

                else -> {
                    appendInlineMarkdown(line, boldStyle, italicStyle, strikethroughStyle)
                }
            }
            if (index < lines.lastIndex) append("\n")
        }
    }

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    boldStyle: SpanStyle,
    italicStyle: SpanStyle,
    strikethroughStyle: SpanStyle,
) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(boldStyle) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(strikethroughStyle) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '*' || text[i] == '_' -> {
                val delim = text[i]
                val end = text.indexOf(delim, i + 1)
                if (end > i && end > i + 1) {
                    withStyle(italicStyle) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
