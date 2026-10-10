package com.leyline.crucible.intellij.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes
import com.leyline.crucible.intellij.psi.CrucibleFile

/**
 * Where the Crucible live templates (`liveTemplates/Crucible.xml`) apply: in
 * a `.cru` file, outside comments and strings.
 */
class CrucibleTemplateContext : TemplateContextType("Crucible") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file as? CrucibleFile ?: return false
        val offset = templateActionContext.startOffset
        if (file.findElementAt(offset)?.node?.elementType.let { it in CrucibleTokenTypes.COMMENTS.types || it == CrucibleTokenTypes.STRING }) {
            return false
        }
        if (offset == 0) return true
        val previous = file.findElementAt(offset - 1)?.node ?: return true
        // A comment runs to the end of its line, so a caret there is still in
        // it; so is a caret after a string that has not been closed yet.
        return previous.elementType !in CrucibleTokenTypes.COMMENTS.types &&
            !(previous.elementType == CrucibleTokenTypes.STRING && !isClosed(previous.text))
    }

    /** A string token ends in a quote that no backslash escapes. */
    private fun isClosed(text: String): Boolean {
        if (text.length < 2 || !text.endsWith('"')) return false
        val backslashes = text.dropLast(1).takeLastWhile { it == '\\' }.length
        return backslashes % 2 == 0
    }
}
