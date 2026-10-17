package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.accentContentColor

/**
 * Raccourcis modèles du viseur : une étoile pour mettre le modèle actif en favori, puis une puce
 * par favori. Un appui applique le modèle, un second appui sur le modèle actif le retire.
 */
@Composable
internal fun ModelQuickBar(
    favorites: List<LutInfo>,
    currentLut: LutInfo?,
    onSelect: (LutInfo) -> Unit,
    onClear: () -> Unit,
    onToggleFavorite: (LutInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentId = currentLut?.id?.takeIf { it != "none" }
    if (favorites.isEmpty() && currentId == null) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentLut != null && currentId != null) {
            val label = stringResource(
                if (currentLut.isFavorite) R.string.model_remove_favorite else R.string.model_add_favorite
            )
            QuickChip(
                text = if (currentLut.isFavorite) "★" else "☆",
                selected = currentLut.isFavorite,
                description = label,
                onClick = { onToggleFavorite(currentLut) }
            )
        }
        favorites.forEach { lut ->
            val selected = lut.id == currentId
            QuickChip(
                text = lut.getName(),
                selected = selected,
                description = lut.getName(),
                onClick = { if (selected) onClear() else onSelect(lut) }
            )
        }
    }
}

@Composable
private fun QuickChip(
    text: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = text,
        color = if (selected) accentContentColor(AccentColor) else Color.White,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (selected) TextStyle.Default else TextStyle(shadow = ViewfinderTextShadow),
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AccentColor else Color.Black.copy(alpha = 0.28f))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
