package com.hinnka.mycamera.camera

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class NightExposurePlan(
    val sensitivityIso: Int,
    val exposureTimeNs: Long,
    val liftEv: Double,
)

/**
 * Exposition de chaque image du mode Nuit : plus la scène est sombre, plus on l'éclaircit
 * (de 0 à [MAX_LIFT_EV]), en allongeant d'abord la pose (jusqu'à [MAX_SHUTTER_NS], l'alignement
 * multi-images absorbe le léger bougé) puis en montant l'ISO. Les 12 images empilées compensent
 * le bruit ajouté. La luminosité de la scène est estimée par ISO × temps de pose (ISO·s) :
 * ~0,1 en plein soleil, ~5 dans une pièce éclairée, 100+ dans une pièce sombre.
 */
internal object NightExposurePlanner {
    const val MAX_LIFT_EV = 2.0
    const val MAX_SHUTTER_NS = 1_000_000_000L / 8

    // Pente de l'éclaircissement (EV ajoutés par EV de scène sombre) et seuil en dessous duquel
    // on ne touche à rien (scène claire).
    private const val LIFT_SLOPE = 0.6
    private const val MIN_LIFT_EV = 0.25

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
        val sceneIsoSeconds = baseIso.toDouble() * baseExposureTimeNs.toDouble() / 1e9
        val liftEv = (LIFT_SLOPE * log2(sceneIsoSeconds)).coerceIn(0.0, MAX_LIFT_EV)
        if (liftEv < MIN_LIFT_EV) return null

        val maxShutterNs = max(min(exposureTimeUpperNs, MAX_SHUTTER_NS), baseExposureTimeNs)
            .coerceIn(exposureTimeLowerNs, exposureTimeUpperNs)
        val targetProduct = baseIso.toDouble() * baseExposureTimeNs.toDouble() * 2.0.pow(liftEv)
        val shutterNs = (baseExposureTimeNs * 2.0.pow(liftEv))
            .roundToLong()
            .coerceIn(exposureTimeLowerNs, maxShutterNs)
        val iso = (targetProduct / shutterNs.toDouble())
            .roundToInt()
            .coerceIn(isoLower, isoUpper)
        return NightExposurePlan(sensitivityIso = iso, exposureTimeNs = shutterNs, liftEv = liftEv)
    }
}
