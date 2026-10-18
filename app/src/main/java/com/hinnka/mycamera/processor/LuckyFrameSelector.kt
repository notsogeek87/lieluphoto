package com.hinnka.mycamera.processor

import com.hinnka.mycamera.camera.GyroExposureWindow
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Choix des images d'une rafale empilée (non RAW) d'après le gyroscope (« lucky imaging »).
 *
 * L'empilement aligne tout sur la première image transmise : historiquement la première de la
 * rafale, souvent la plus bougée (le doigt vient d'appuyer). On met en tête l'image dont la
 * rotation pendant la pose est la plus faible, et on écarte celles nettement plus floues que la
 * médiane, en gardant toujours au moins [MIN_KEPT] images.
 */
internal object LuckyFrameSelector {
    const val MIN_KEPT = 5

    // Une image est écartée si son bougé dépasse 2× la médiane...
    private const val REJECT_FACTOR = 2f

    // ...sauf si elle reste sous ~0,5 mrad (≈ 1,5 px avec une focale de ~3000 px) : nette.
    private const val SHARP_ENOUGH_RAD = 0.0005f
    private const val MIN_COVERAGE = 0.5f

    /** Rotation (rad) pendant la pose, ou null si le gyroscope n'a pas couvert la pose. */
    fun blurRad(window: GyroExposureWindow): Float? {
        if (window.sampleCount <= 0 || window.coverageRatio < MIN_COVERAGE) return null
        val r = window.integratedRotationRad
        if (r.size < 3) return null
        val norm = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2])
        return norm.takeIf { it.isFinite() }
    }

    /**
     * Indices à empiler, dans l'ordre de traitement : la plus nette d'abord (référence), puis
     * les autres images gardées dans l'ordre chronologique. Sans mesure gyroscope complète,
     * l'ordre d'origine est conservé tel quel.
     */
    fun order(blurRad: List<Float?>): List<Int> {
        if (blurRad.size < 2 || blurRad.any { it == null }) return blurRad.indices.toList()
        val values = blurRad.map { it!! }
        val ranked = values.indices.sortedBy { values[it] }
        val median = values[ranked[ranked.size / 2]]
        val limit = max(median * REJECT_FACTOR, SHARP_ENOUGH_RAD)
        val kept = ranked.filterIndexed { rank, index ->
            rank < MIN_KEPT || values[index] <= limit
        }.toSet()
        val reference = ranked.first()
        return listOf(reference) + values.indices.filter { it != reference && it in kept }
    }
}
