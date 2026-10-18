package com.hinnka.mycamera.camera

/**
 * Diagnostic du mode Nuit écrit dans le champ EXIF « Software » de la photo, pour comprendre
 * sur une photo réelle ce que le mode a fait (empilement actif, pose, ISO, rendu appliqué).
 * Texte ASCII uniquement (contrainte EXIF).
 */
object NightDiagnostics {
    private const val MAX_AGE_MS = 120_000L

    @Volatile
    private var captureNote: String? = null

    @Volatile
    private var planNote: String? = null

    @Volatile
    private var renderNote: String? = null

    @Volatile
    private var stackNote: String? = null

    @Volatile
    private var updatedAtMs = 0L

    /** Début de prise : remet à zéro la décision d'exposition de la prise précédente. */
    fun recordCapture(note: String) {
        captureNote = note
        planNote = "plan[none]"
        stackNote = null
        updatedAtMs = System.currentTimeMillis()
    }

    fun recordStack(note: String) {
        stackNote = note
        updatedAtMs = System.currentTimeMillis()
    }

    fun recordPlan(note: String) {
        planNote = note
        updatedAtMs = System.currentTimeMillis()
    }

    fun recordRender(note: String) {
        renderNote = note
        updatedAtMs = System.currentTimeMillis()
    }

    /** Diagnostic récent (prise de moins de 2 min), ou null hors mode Nuit. */
    fun recent(): String? {
        if (System.currentTimeMillis() - updatedAtMs > MAX_AGE_MS) return null
        val parts = listOfNotNull(renderNote, captureNote, planNote, stackNote)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun clear() {
        captureNote = null
        planNote = null
        stackNote = null
        renderNote = null
        updatedAtMs = 0L
    }
}
