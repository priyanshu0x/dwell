package com.droidslife.screensaver.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

// A task's content/notes can contain markdown (Todoist sends it). We render a
// small, common subset so a title shows "all your devices" instead of the raw
// "[all your devices](https://…)".
private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

/**
 * Renders the supported markdown subset — `[text](url)`, `**bold**`, `*italic*`,
 * `` `code` ``, `~~strike~~` — into an [AnnotatedString].
 *
 * When [interactive] is true, links carry a [LinkAnnotation.Url] so a `Text`
 * opens them via the platform Uri handler. When false (the default for list /
 * matrix rows) the link label is styled but inert, so it never competes with a
 * row's own click-to-open-detail gesture.
 */
fun renderTaskMarkdown(
    raw: String,
    linkColor: Color,
    interactive: Boolean = false,
): AnnotatedString = buildAnnotatedString { appendMarkdown(raw, linkColor, interactive) }

private fun AnnotatedString.Builder.appendMarkdown(text: String, linkColor: Color, interactive: Boolean) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val link = LINK.matchAt(text, i)
        when {
            link != null -> {
                flush()
                val label = link.groupValues[1]
                val url = link.groupValues[2]
                val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                if (interactive) {
                    // A listener routes through openLink; providing it also stops
                    // the default UriHandler from firing too.
                    val link = LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(style),
                        linkInteractionListener = LinkInteractionListener { openLink(url) },
                    )
                    withLink(link) {
                        appendMarkdown(label, linkColor, interactive = false)
                    }
                } else {
                    withStyle(style) { appendMarkdown(label, linkColor, interactive = false) }
                }
                i += link.value.length
            }

            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    flush()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendMarkdown(text.substring(i + 2, end), linkColor, interactive)
                    }
                    i = end + 2
                } else { plain.append(text[i]); i++ }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 1) {
                    flush()
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendMarkdown(text.substring(i + 2, end), linkColor, interactive)
                    }
                    i = end + 2
                } else { plain.append(text[i]); i++ }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(i + 1, end)) // code spans aren't styled further
                    }
                    i = end + 1
                } else { plain.append(text[i]); i++ }
            }

            // Single * = italic. (`_` is intentionally not treated as italic — it
            // appears too often inside plain words and URLs to flip safely.)
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    flush()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendMarkdown(text.substring(i + 1, end), linkColor, interactive)
                    }
                    i = end + 1
                } else { plain.append(text[i]); i++ }
            }

            else -> { plain.append(text[i]); i++ }
        }
    }
    flush()
}
