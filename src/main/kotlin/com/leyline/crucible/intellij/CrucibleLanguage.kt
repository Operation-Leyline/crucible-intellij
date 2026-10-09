package com.leyline.crucible.intellij

import com.intellij.lang.Language

/** Crucible's modelling language, written in `.cru` files. */
object CrucibleLanguage : Language("Crucible") {
    private fun readResolve(): Any = CrucibleLanguage

    override fun getDisplayName(): String = "Crucible"
}
