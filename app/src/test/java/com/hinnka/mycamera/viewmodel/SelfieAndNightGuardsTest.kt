package com.hinnka.mycamera.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfieAndNightGuardsTest {
    @Test
    fun autoSharpnessIsAddedUpToCeiling() {
        assertEquals(0.25f, 0f.withAutoSharpnessBoost(0.25f), 1e-6f)
        assertEquals(AUTO_SHARPNESS_CEILING, 0.4f.withAutoSharpnessBoost(0.25f), 1e-6f)
    }

    @Test
    fun autoSharpnessKeepsSharperOrSofterRecipes() {
        // Recette déjà plus nette que le plafond : inchangée.
        assertEquals(0.8f, 0.8f.withAutoSharpnessBoost(0.25f), 1e-6f)
        // Look volontairement doux : on remonte un peu sans annuler le choix.
        assertEquals(-0.25f, (-0.5f).withAutoSharpnessBoost(0.25f), 1e-6f)
    }

    @Test
    fun nightBoostDoesNotStackSharpnessBeyondCeiling() {
        val boosted = com.hinnka.mycamera.model.ColorRecipeParams(sharpness = 0.4f).withNightBoost(0f)
        assertEquals(AUTO_SHARPNESS_CEILING, boosted.sharpness, 1e-6f)
    }

    @Test
    fun frontCameraForcesMultiFrameUnlessLivePhotoOrMultipleExposure() {
        assertTrue(resolveEffectiveUseJpgMax(false, false, frontCamera = true, useLivePhoto = false, useMultipleExposure = false))
        assertFalse(resolveEffectiveUseJpgMax(false, false, frontCamera = true, useLivePhoto = true, useMultipleExposure = false))
        assertFalse(resolveEffectiveUseJpgMax(false, false, frontCamera = true, useLivePhoto = false, useMultipleExposure = true))
    }

    @Test
    fun rearCameraFollowsUserAndNightChoices() {
        assertFalse(resolveEffectiveUseJpgMax(false, false, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
        assertTrue(resolveEffectiveUseJpgMax(true, false, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
        assertTrue(resolveEffectiveUseJpgMax(false, true, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
    }
}
