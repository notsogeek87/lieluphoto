package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.ui.theme.AccentColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val IrisBladeCount = 6
private const val ApertureDialCollapseDelayMillis = 2500L
private val RulerWidth = 168.dp
private val RulerHeight = 28.dp
private val RulerEdgePadding = 6.dp

/**
 * Sélecteur d'ouverture du mode Portrait, posé au niveau du zoom. Il évoque une bague d'objectif :
 * un diaphragme dont l'ouverture se referme quand le nombre f augmente, la valeur en clair, et une
 * règle graduée par valeurs d'ouverture que l'on touche ou fait glisser.
 *
 * Replié par défaut en une petite pastille discrète (diaphragme + valeur), pour ne pas couvrir le
 * viseur. Un appui déploie la règle ; elle se replie seule après [ApertureDialCollapseDelayMillis]
 * sans interaction, comme la barre de zoom.
 */
@Composable
internal fun ApertureDial(
    aperture: Float,
    onApertureChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val stops = VirtualApertureStops
    val selected = stops.indices.minByOrNull { abs(stops[it] - aperture) } ?: 0
    val currentOnApertureChange by rememberUpdatedState(onApertureChange)
    val currentSelected by rememberUpdatedState(selected)
    var expanded by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableStateOf(0L) }
    LaunchedEffect(expanded, lastInteraction) {
        if (expanded) {
            delay(ApertureDialCollapseDelayMillis)
            expanded = false
        }
    }
    // 1 = diaphragme grand ouvert (f/1.0), 0 = fermé au maximum (f/16).
    val openness by animateFloatAsState(
        targetValue = 1f - selected / (stops.size - 1).toFloat(),
        label = "apertureIrisOpening"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.28f else 0.12f,
        label = "apertureDialBackground"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.18f else 0f,
        label = "apertureDialBorder"
    )
    val description = stringResource(R.string.virtual_aperture_dial_description)
    val shape = RoundedCornerShape(20.dp)
    val accent = AccentColor

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .border(0.5.dp, Color.White.copy(alpha = borderAlpha), shape)
            // La règle consomme ses propres appuis : ce clic ne vient que de la pastille.
            .clickable {
                expanded = !expanded
                lastInteraction = System.currentTimeMillis()
            }
            .animateContentSize()
            .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = if (expanded) 6.dp else 4.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 6.dp)
    ) {
        ApertureIris(
            openness = openness,
            accent = accent,
            modifier = Modifier.size(if (expanded) 22.dp else 16.dp)
        )
        Text(
            text = "ƒ/" + formatVirtualApertureFStop(stops[selected]),
            color = Color.White,
            fontSize = if (expanded) 13.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = ViewfinderTextShadow),
            maxLines = 1
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            ApertureRuler(
                count = stops.size,
                selected = selected,
                accent = accent,
                onSelect = { index ->
                    lastInteraction = System.currentTimeMillis()
                    if (index != currentSelected) currentOnApertureChange(stops[index])
                }
            )
        }
    }
}

/** Diaphragme à six lames : l'ouverture hexagonale grandit ou rétrécit avec [openness]. */
@Composable
private fun ApertureIris(openness: Float, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f - 1.dp.toPx()
        val openingRadius = outerRadius * (0.22f + 0.6f * openness)
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Les lames : un trait de chaque sommet de l'ouverture vers la bague extérieure.
        for (blade in 0 until IrisBladeCount) {
            val vertex = polarPoint(center, openingRadius, bladeAngle(blade))
            val edge = polarPoint(center, outerRadius, bladeAngle(blade) + PI / IrisBladeCount * 1.4)
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = vertex,
                end = edge,
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val opening = Path().apply {
            for (blade in 0 until IrisBladeCount) {
                val vertex = polarPoint(center, openingRadius, bladeAngle(blade))
                if (blade == 0) moveTo(vertex.x, vertex.y) else lineTo(vertex.x, vertex.y)
            }
            close()
        }
        drawPath(opening, color = accent)
    }
}

/** Règle graduée : un cran par valeur d'ouverture, le cran choisi est plus long et en couleur. */
@Composable
private fun ApertureRuler(
    count: Int,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    Canvas(
        modifier = modifier
            .width(RulerWidth)
            .height(RulerHeight)
            .pointerInput(count) {
                detectTapGestures { offset ->
                    currentOnSelect(stopIndexAt(offset.x, size.width.toFloat(), RulerEdgePadding.toPx(), count))
                }
            }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        currentOnSelect(stopIndexAt(offset.x, size.width.toFloat(), RulerEdgePadding.toPx(), count))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        currentOnSelect(
                            stopIndexAt(change.position.x, size.width.toFloat(), RulerEdgePadding.toPx(), count)
                        )
                    }
                )
            }
    ) {
        val edge = RulerEdgePadding.toPx()
        val step = (size.width - 2f * edge) / (count - 1)
        val centerY = size.height / 2f
        for (index in 0 until count) {
            val x = edge + index * step
            val isSelected = index == selected
            val halfHeight = (if (isSelected) 10.dp else 4.dp).toPx()
            drawLine(
                color = if (isSelected) accent else Color.White.copy(alpha = 0.5f),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = (if (isSelected) 2.5.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun stopIndexAt(x: Float, width: Float, edge: Float, count: Int): Int {
    val fraction = ((x - edge) / (width - 2f * edge)).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt()
}

/** Angle du sommet [blade] de l'ouverture, le premier pointant vers le haut. */
private fun bladeAngle(blade: Int): Double = -PI / 2 + blade * 2 * PI / IrisBladeCount

private fun DrawScope.polarPoint(center: Offset, radius: Float, angle: Double): Offset =
    Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())

/**
 * Pastille posée à la place du cadran tant que le Portrait attend son modèle de profondeur :
 * l'utilisateur voit que le mode se prépare et où en est le téléchargement. L'appui ouvre le détail.
 */
@Composable
internal fun PortraitModelProgressChip(
    modelState: DepthModelDownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloading = modelState as? DepthModelDownloadState.Downloading
    val progress = downloading?.takeIf { !it.isInstalling }?.progress
    val detail = when {
        modelState is DepthModelDownloadState.Importing -> stringResource(R.string.depth_model_importing)
        downloading?.isInstalling == true -> stringResource(R.string.depth_model_installing)
        progress != null -> stringResource(R.string.depth_model_downloading_progress, (progress * 100f).roundToInt())
        else -> stringResource(R.string.depth_model_downloading)
    }
    val shape = RoundedCornerShape(20.dp)
    val accent = AccentColor
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.portrait_model_preparing, detail),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        val indicatorModifier = Modifier
            .width(200.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = indicatorModifier
            )
        } else {
            LinearProgressIndicator(
                color = accent,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = indicatorModifier
            )
        }
    }
}
