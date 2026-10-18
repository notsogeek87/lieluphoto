package com.hinnka.mycamera.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NightExposurePlannerTest {
    @Test
    fun brightSceneIsLeftUntouched() {
        assertNull(NightExposurePlanner.sceneLiftEv(baseIso = 100, baseExposureTimeNs = 1_000_000L))
    }

    @Test
    fun litRoomIsLeftUntouched() {
        // Pièce éclairée (selfie intérieur) : ISO 1000 à 1/30 s ≈ 33 ISO·s, jamais éclaircie.
        assertNull(NightExposurePlanner.sceneLiftEv(baseIso = 1000, baseExposureTimeNs = 33_000_000L))
        // ISO 150 à 1/30 s ≈ 5 ISO·s.
        assertNull(NightExposurePlanner.sceneLiftEv(baseIso = 150, baseExposureTimeNs = 33_000_000L))
    }

    @Test
    fun liftGrowsGraduallyWithDarkness() {
        // ~57 ISO·s : un demi-EV de scène sombre au-dessus du seuil, éclaircissement modéré (0,3 EV),
        // sous le plafond.
        val lift = NightExposurePlanner.sceneLiftEv(baseIso = 1720, baseExposureTimeNs = 33_000_000L)
        assertNotNull(lift)
        assertEquals(0.3, lift!!, 0.05)
        assert(lift < NightExposurePlanner.MAX_LIFT_EV)
    }

    @Test
    fun darkSceneIsCappedAtMaxLift() {
        val lift = NightExposurePlanner.sceneLiftEv(baseIso = 4000, baseExposureTimeNs = 40_000_000L)
        assertEquals(NightExposurePlanner.MAX_LIFT_EV, lift ?: -1.0, 1e-9)
    }

    @Test
    fun steadyPhoneUsesLongShutterAndReachesFullLift() {
        val plan = NightExposurePlanner.plan(
            baseIso = 2000,
            baseExposureTimeNs = 40_000_000L,
            isoLower = 50,
            isoUpper = 6400,
            exposureTimeLowerNs = 10_000L,
            exposureTimeUpperNs = 1_000_000_000L,
            liftEv = 3.0,
            angularSpeedRadS = 0.0001f,
        )
        assertNotNull(plan)
        assertEquals(NightExposurePlanner.MAX_SHUTTER_NS, plan!!.exposureTimeNs)
        assertEquals(3.0, plan.realizedEv, 0.05)
    }

    @Test
    fun handheldIsoLimitLeavesResidualForPostProcessing() {
        val plan = NightExposurePlanner.plan(
            baseIso = 2000,
            baseExposureTimeNs = 40_000_000L,
            isoLower = 50,
            isoUpper = 3200,
            exposureTimeLowerNs = 10_000L,
            exposureTimeUpperNs = 1_000_000_000L,
            liftEv = 3.0,
            angularSpeedRadS = 0.15f,
        )
        assertNotNull(plan)
        assertEquals(100_000_000L, plan!!.exposureTimeNs)
        assertEquals(3200, plan.sensitivityIso)
        assertEquals(2.0, plan.realizedEv, 0.05)
    }

    @Test
    fun galaxyFoldStairsCaseReachesFullLiftWithinSensorIsoRange() {
        // Diagnostic EXIF réel : base ISO 7227 à 1/25 s, gyroscope 0,055 rad/s, ISO max 3200.
        val plan = NightExposurePlanner.plan(
            baseIso = 7227,
            baseExposureTimeNs = 39_965_296L,
            isoLower = 50,
            isoUpper = 3200,
            exposureTimeLowerNs = 10_000L,
            exposureTimeUpperNs = 1_000_000_000L,
            liftEv = 1.0,
            angularSpeedRadS = 0.054858368f,
        )
        assertNotNull(plan)
        assertEquals(273_000_000.0, plan!!.exposureTimeNs.toDouble(), 2_000_000.0)
        assertEquals(1.0, plan.realizedEv, 0.05)
    }
}
