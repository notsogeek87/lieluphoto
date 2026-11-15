package com.hinnka.mycamera.processor

enum class BokehStyle(
    val persistedName: String,
    val shaderValue: Int,
) {
    DEFAULT("DEFAULT", 0),
    NATURAL("BUBBLE", 1),
    BUBBLE("SOAP_BUBBLE", 2);

    companion object {
        /** Style des nouvelles photos et éditions. Les anciennes fiches gardent leur style. */
        val INITIAL = NATURAL

        fun fromPersistedName(value: String?): BokehStyle =
            entries.firstOrNull { it.persistedName.equals(value, ignoreCase = true) }
                ?: DEFAULT
    }
}
