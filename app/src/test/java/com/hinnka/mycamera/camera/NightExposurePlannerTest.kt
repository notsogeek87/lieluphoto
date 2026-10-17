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
    fun darkSceneIsCappedAtMaxLift() {
        val lift = NightExposurePlanner.sceneLiftEv(baseIso = 2000, baseExposureTimeNs = 40_000_000L)
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
            angularSpeedRadS = 0.05f,
        )
        assertNotNull(plan)
        assertEquals(100_000_000L, plan!!.exposureTimeNs)
        assertEquals(3200, plan.sensitivityIso)
        assertEquals(2.0, plan.realizedEv, 0.05)
    }
}
