package com.patslaurel.resibo.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patslaurel.resibo.ui.check.CheckResult
import java.net.URI

@Composable
fun NoteCard(
    checkResult: CheckResult,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = checkResult.claim,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            HorizontalDivider()

            MarkdownText(markdown = checkResult.analysis)

            if (checkResult.sources.isNotEmpty()) {
                HorizontalDivider()

                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.labelLarge,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    checkResult.sources.forEach { source ->
                        val domain = extractDomain(source.reviewUrl)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier =
                                Modifier.noRippleClickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.reviewUrl))
                                    context.startActivity(intent)
                                },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Text(
                text = formatResponseTime(checkResult.responseTimeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )

            FilledTonalButton(
                onClick = {
                    val shareText = buildShareText(checkResult)
                    val sendIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Share result",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun extractDomain(url: String): String =
    try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }

private fun formatResponseTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return if (totalSeconds >= 60) {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "Responded in ${minutes}m ${seconds}s"
    } else {
        "Responded in ${totalSeconds}s"
    }
}

private fun buildShareText(result: CheckResult): String {
    val analysisSnippet =
        if (result.analysis.length > 200) {
            result.analysis.take(200) + "..."
        } else {
            result.analysis
        }

    val sourcesBlock =
        if (result.sources.isNotEmpty()) {
            val sourceLines =
                result.sources.joinToString("\n") { source ->
                    "\u2022 ${extractDomain(source.reviewUrl)}"
                }
            "\n\nSources:\n$sourceLines"
        } else {
            ""
        }

    return buildString {
        append("\uD83D\uDD0D Resibo Fact-Check\n\n")
        append("Claim: \"${result.claim}\"\n\n")
        append(analysisSnippet)
        append(sourcesBlock)
        append("\n\nChecked via Resibo")
    }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick,
        ),
    )
