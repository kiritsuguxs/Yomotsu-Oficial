package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.presentation.core.components.Badge
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.ExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get



@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}


private fun getFlagEmoji(language: String): String {
    return when (language.lowercase()) {
        "pt-br", "pt" -> "🇧🇷"
        "en" -> "🇺🇸"
        "es", "es-es" -> "🇪🇸"
        "es-la" -> "🇲🇽"
        "ja" -> "🇯🇵"
        "ko" -> "🇰🇷"
        "zh", "zh-hans", "zh-hant" -> "🇨🇳"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "it" -> "🇮🇹"
        "ru" -> "🇷🇺"
        "id" -> "🇮🇩"
        "vi" -> "🇻🇳"
        "th" -> "🇹🇭"
        "ar" -> "🇸🇦"
        "tr" -> "🇹🇷"
        "pl" -> "🇵🇱"
        "ro" -> "🇷🇴"
        else -> ""
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
    sourceId: Long? = null,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        val icon = try {
            val extensionManager = Injekt.get<ExtensionManager>()
            sourceId?.let { extensionManager.getAppIconForSource(it)?.toBitmap()?.asImageBitmap() }
        } catch (e: Throwable) {
            null
        }
        
        if (icon != null) {
            val flag = getFlagEmoji(sourceLanguage)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(2.dp)
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                if (flag.isNotEmpty()) {
                    Text(
                        text = flag,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            Badge(
                text = sourceLanguage.uppercase(),
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
        }
    }
}
