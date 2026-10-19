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
    fun selfieClarityIsCappedForSkin() {
        assertEquals(0.15f, 0f.withAutoBoost(0.15f, AUTO_CLARITY_CEILING), 1e-6f)
        assertEquals(AUTO_CLARITY_CEILING, 0.2f.withAutoBoost(0.15f, AUTO_CLARITY_CEILING), 1e-6f)
    }

    @Test
    fun sharpenRadiusScalesWithResolution() {
        val shader = com.hinnka.mycamera.lut.SrgbSharpnessShader
        // Petites images (aperçus, miniatures) : rayon d'origine.
        assertEquals(1f, shader.radiusFor(1080, 810), 1e-6f)
        // 12 Mpx (4000 × 3000) : 2 px.
        assertEquals(2f, shader.radiusFor(4000, 3000), 1e-6f)
        // 50 Mpx : plafonné.
        assertEquals(2.5f, shader.radiusFor(8160, 6120), 1e-6f)
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
        // Arrière : empilement toujours actif (RAW Max automatique), sauf Live Photo.
        assertTrue(resolveEffectiveUseJpgMax(false, false, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
        assertFalse(resolveEffectiveUseJpgMax(false, false, frontCamera = false, useLivePhoto = true, useMultipleExposure = false))
        assertTrue(resolveEffectiveUseJpgMax(true, false, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
        assertTrue(resolveEffectiveUseJpgMax(false, true, frontCamera = false, useLivePhoto = false, useMultipleExposure = false))
    }

    @Test
    fun selfieStacksAtLeastEightFramesAndNightAtLeastFifteen() {
        val front = com.hinnka.mycamera.camera.MultiFrameConfig.FRONT_SELFIE_FRAME_COUNT
        val night = com.hinnka.mycamera.camera.MultiFrameConfig.NIGHT_FRAME_COUNT
        assertEquals(5, resolveEffectiveFrameCount(5, nightMode = false, frontCamera = false))
        assertEquals(front, resolveEffectiveFrameCount(5, nightMode = false, frontCamera = true))
        assertEquals(12, resolveEffectiveFrameCount(12, nightMode = false, frontCamera = true))
        assertEquals(night, resolveEffectiveFrameCount(5, nightMode = true, frontCamera = true))
    }
}
