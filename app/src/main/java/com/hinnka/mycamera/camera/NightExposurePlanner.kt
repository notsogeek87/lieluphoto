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
    val realizedEv: Double,
)

/**
 * Exposition de chaque image du mode Nuit.
 *
 * - Éclaircissement : plus la scène est sombre, plus on éclaircit (de 0 à [MAX_LIFT_EV]). La
 *   luminosité est estimée par ISO × temps de pose (ISO·s) : ~0,1 en plein soleil, ~5 dans une
 *   pièce éclairée, 100+ dans une pièce sombre.
 * - Pose : la plus longue que permet le gyroscope. Le flou de bougé en pixels vaut à peu près
 *   vitesse angulaire × pose × focale (px) / facteur stabilisation, d'où pose max = budget / vitesse.
 *   Téléphone posé : jusqu'à [MAX_SHUTTER_NS] ; tenu à la main : ~1/10 s ; en marchant : [MIN_SHUTTER_NS].
 *   Sans gyroscope : [DEFAULT_SHUTTER_NS].
 * - L'ISO est ensuite le plus bas possible pour atteindre la luminosité visée. Les images
 *   empilées (voir MultiFrameConfig.NIGHT_FRAME_COUNT) compensent le bruit restant.
 */
internal object NightExposurePlanner {
    const val MAX_LIFT_EV = 3.0
    const val MAX_SHUTTER_NS = 1_000_000_000L / 3
    const val DEFAULT_SHUTTER_NS = 1_000_000_000L / 8
    const val MIN_SHUTTER_NS = 1_000_000_000L / 30

    // Pente de l'éclaircissement (EV ajoutés par EV de scène sombre) et seuil en dessous duquel
    // on ne touche à rien (scène claire).
    private const val LIFT_SLOPE = 0.6
    private const val MIN_LIFT_EV = 0.25

    /** Éclaircissement maximal rattrapé en post-traitement (recette), en EV. */
    const val MAX_POST_EXPOSURE_EV = 2.0

    // Pose max (s) = MOTION_BUDGET / vitesse angulaire (rad/s) : ~3 px de flou résiduel avec
    // une focale de ~3000 px et une stabilisation optique qui en retire ~5/6.
    private const val MOTION_BUDGET = 0.005
    private const val MIN_ANGULAR_SPEED = 1e-4

    /** Éclaircissement visé (EV) pour une scène, ou null si elle est assez claire pour ne rien changer. */
    fun sceneLiftEv(baseIso: Int, baseExposureTimeNs: Long): Double? {
        if (baseIso <= 0 || baseExposureTimeNs <= 0L) return null
        val sceneIsoSeconds = baseIso.toDouble() * baseExposureTimeNs.toDouble() / 1e9
        val liftEv = (LIFT_SLOPE * log2(sceneIsoSeconds)).coerceIn(0.0, MAX_LIFT_EV)
        return liftEv.takeIf { it >= MIN_LIFT_EV }
    }

    /**
     * Exposition manuelle qui réalise au mieux [liftEv]. Les limites (pose max du gyroscope, ISO
     * max du capteur) peuvent empêcher de l'atteindre : l'écart ([NightExposurePlan.liftEv] moins
     * [NightExposurePlan.realizedEv]) est rattrapé en post-traitement.
     */
    fun plan(
        baseIso: Int,
        baseExposureTimeNs: Long,
        isoLower: Int,
        isoUpper: Int,
        exposureTimeLowerNs: Long,
        exposureTimeUpperNs: Long,
        liftEv: Double,
        angularSpeedRadS: Float?,
    ): NightExposurePlan? {
        if (baseIso <= 0 || baseExposureTimeNs <= 0L) return null
        if (isoLower <= 0 || isoUpper < isoLower) return null
        if (exposureTimeLowerNs <= 0L || exposureTimeUpperNs < exposureTimeLowerNs) return null

        val motionLimitNs = if (angularSpeedRadS != null && angularSpeedRadS.isFinite()) {
            (MOTION_BUDGET / max(angularSpeedRadS.toDouble(), MIN_ANGULAR_SPEED) * 1e9)
                .roundToLong()
                .coerceIn(MIN_SHUTTER_NS, MAX_SHUTTER_NS)
        } else {
            DEFAULT_SHUTTER_NS
        }
        val maxShutterNs = max(min(exposureTimeUpperNs, motionLimitNs), baseExposureTimeNs)
            .coerceIn(exposureTimeLowerNs, exposureTimeUpperNs)
        val targetProduct = baseIso.toDouble() * baseExposureTimeNs.toDouble() * 2.0.pow(liftEv)
        // Pose la plus longue permise, sans dépasser ce qu'il faut à l'ISO minimal.
        val shutterNs = min(maxShutterNs.toDouble(), targetProduct / isoLower)
            .roundToLong()
            .coerceIn(exposureTimeLowerNs, maxShutterNs)
        val iso = (targetProduct / shutterNs.toDouble())
            .roundToInt()
            .coerceIn(isoLower, isoUpper)
        val realizedEv = log2(
            iso.toDouble() * shutterNs.toDouble() /
                (baseIso.toDouble() * baseExposureTimeNs.toDouble())
        ).coerceAtLeast(0.0)
        return NightExposurePlan(
            sensitivityIso = iso,
            exposureTimeNs = shutterNs,
            liftEv = liftEv,
            realizedEv = realizedEv,
        )
    }
}
