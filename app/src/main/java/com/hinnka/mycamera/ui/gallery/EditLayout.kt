package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hinnka.mycamera.ui.theme.AccentColor

/** Surface des zones d'outils (rail et panneau), distincte de la zone image. */
internal val EditToolsSurfaceColor = Color(0xFF121212)

private val EditToolRailWidth = 72.dp
private val EditToolMinTouchSize = 48.dp
private val EditLandscapePanelMaxWidth = 360.dp
private const val EDIT_PORTRAIT_PANEL_MAX_HEIGHT_FRACTION = 0.5f

/** Emplacements des zones de l'écran d'édition, portés par `Modifier.layoutId`. */
internal enum class EditLayoutSlot { TopBar, Histogram, Preview, Panel, Rail }

/** Un outil du rail : [id] est la catégorie d'édition qu'il ouvre. */
internal data class EditTool(
    val id: Int,
    val title: String,
    val icon: ImageVector,
)

/**
 * Disposition de l'écran d'édition en zones disjointes : l'image n'est jamais recouverte.
 *
 * Portrait : barre d'outils (rail) sur le bord droit ; à gauche du rail, barre du haut,
 * histogramme, image, puis panneau de réglages sous l'image.
 * Paysage : rail à gauche, panneau de réglages en colonne à droite ; au centre, barre du haut,
 * histogramme, puis image.
 *
 * Les enfants sont repérés par [EditLayoutSlot] ; tout enfant sans emplacement (la photo de
 * référence flottante) est posé en haut à gauche et dessiné au-dessus.
 */
@Composable
internal fun EditScreenLayout(
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        fun Measurable.slot(): EditLayoutSlot? = layoutId as? EditLayoutSlot
        fun slotMeasurable(slot: EditLayoutSlot) = measurables.firstOrNull { it.slot() == slot }
        fun Measurable?.measureWith(maxWidth: Int, maxHeight: Int): Placeable? =
            this?.measure(Constraints(maxWidth = maxWidth.coerceAtLeast(0), maxHeight = maxHeight.coerceAtLeast(0)))

        val rail = slotMeasurable(EditLayoutSlot.Rail).measureWith(EditToolRailWidth.roundToPx(), height)
        val railWidth = rail?.width ?: 0

        val panel = if (isLandscape) {
            val panelMaxWidth = minOf(EditLandscapePanelMaxWidth.roundToPx(), (width * 0.4f).toInt())
            slotMeasurable(EditLayoutSlot.Panel).measureWith(panelMaxWidth, height)
        } else {
            slotMeasurable(EditLayoutSlot.Panel).measureWith(
                width - railWidth,
                (height * EDIT_PORTRAIT_PANEL_MAX_HEIGHT_FRACTION).toInt(),
            )
        }

        val columnX = if (isLandscape) railWidth else 0
        val columnWidth = if (isLandscape) {
            width - railWidth - (panel?.width ?: 0)
        } else {
            width - railWidth
        }
        val columnPanelHeight = if (isLandscape) 0 else panel?.height ?: 0

        val topBar = slotMeasurable(EditLayoutSlot.TopBar).measureWith(columnWidth, height)
        val histogram = slotMeasurable(EditLayoutSlot.Histogram).measureWith(columnWidth, height)
        val topHeight = topBar?.height ?: 0
        val histogramHeight = histogram?.height ?: 0
        val previewHeight = (height - topHeight - histogramHeight - columnPanelHeight).coerceAtLeast(0)
        val preview = slotMeasurable(EditLayoutSlot.Preview)?.measure(
            Constraints.fixed(columnWidth.coerceAtLeast(0), previewHeight)
        )
        val floating = measurables
            .filter { it.slot() == null }
            .map { it.measure(Constraints(maxWidth = width, maxHeight = height)) }

        layout(width, height) {
            preview?.place(columnX, topHeight + histogramHeight)
            topBar?.place(columnX, 0)
            histogram?.place(columnX + (columnWidth - histogram.width) / 2, topHeight)
            if (isLandscape) {
                rail?.place(0, 0)
                panel?.place(width - panel.width, 0)
            } else {
                rail?.place(width - railWidth, 0)
                panel?.place(0, height - panel.height)
            }
            floating.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Rail vertical des catégories d'édition. Un tap sur l'outil actif referme le panneau, un tap
 * sur un autre outil le remplace (un seul panneau ouvert à la fois).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditToolRail(
    tools: List<EditTool>,
    selectedId: Int,
    isPanelOpen: Boolean,
    onToolClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(EditToolRailWidth)
            .fillMaxHeight()
            .background(EditToolsSurfaceColor)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tools.forEach { tool ->
            val isActive = tool.id == selectedId
            val tint = when {
                isActive && isPanelOpen -> AccentColor
                isActive -> Color.White
                else -> Color.White.copy(alpha = 0.6f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(
                        selected = isActive,
                        role = Role.Tab,
                        onClick = { onToolClick(tool.id) },
                    )
                    .heightIn(min = EditToolMinTouchSize)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tint,
                )
                Text(
                    text = tool.title,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
