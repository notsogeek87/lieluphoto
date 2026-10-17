package com.hinnka.mycamera.camera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class NightExposurePlan(
    val sensitivityIso: Int,
    val exposureTimeNs: Long,
)

/**
 * Exposition de chaque image du mode Nuit : on éclaircit la scène de [LIFT_EV] en allongeant
 * d'abord la pose (jusqu'à [MAX_SHUTTER_NS], l'alignement multi-images absorbe le léger bougé),
 * puis en montant l'ISO pour le reste. Les 12 images empilées compensent le bruit ajouté.
 */
internal object NightExposurePlanner {
    const val LIFT_EV = 1.0
    const val MAX_SHUTTER_NS = 1_000_000_000L / 8

    // Scène « sombre » : ISO élevé ou pose déjà longue. En plein jour on ne change rien.
    private const val DARK_ISO = 400
    private const val DARK_SHUTTER_NS = 1_000_000_000L / 30

    fun plan(
        baseIso: Int,
        baseExposureTimeNs: Long,
        isoLower: Int,
        isoUpper: Int,
        exposureTimeLowerNs: Long,
        exposureTimeUpperNs: Long,
    ): NightExposurePlan? {
        if (baseIso <= 0 || baseExposureTimeNs <= 0L) return null
        if (isoLower <= 0 || isoUpper < isoLower) return null
        if (exposureTimeLowerNs <= 0L || exposureTimeUpperNs < exposureTimeLowerNs) return null
        if (baseIso < DARK_ISO && baseExposureTimeNs < DARK_SHUTTER_NS) return null

        val maxShutterNs = max(min(exposureTimeUpperNs, MAX_SHUTTER_NS), baseExposureTimeNs)
            .coerceAtMost(exposureTimeUpperNs)
        val targetProduct = baseIso.toDouble() * baseExposureTimeNs.toDouble() * 2.0.pow(LIFT_EV)
        val shutterNs = (baseExposureTimeNs * 2.0.pow(LIFT_EV))
            .roundToLong()
            .coerceIn(exposureTimeLowerNs, maxShutterNs)
        val iso = (targetProduct / shutterNs.toDouble())
            .roundToInt()
            .coerceIn(isoLower, isoUpper)
        return NightExposurePlan(sensitivityIso = iso, exposureTimeNs = shutterNs)
    }
}
