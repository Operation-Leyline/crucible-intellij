package com.leyline.crucible.intellij

import com.intellij.lang.Language

/**
 * Crucible's modelling language, written in `.cru` files.
 *
 * The id also names the language in Markdown: the platform's LSP hover
 * highlights a code fence tagged `crucible` (the id, matched ignoring case)
 * or `cru` (the file extension) with this language's highlighter, so the
 * declarations `crucible-lsp` shows on hover get the editor's colors.
 */
object CrucibleLanguage : Language("Crucible") {
    private fun readResolve(): Any = CrucibleLanguage

    override fun getDisplayName(): String = "Crucible"
}
