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
        // A comment runs to the end of its line, so a caret there is still in it.
        return offset == 0 || file.findElementAt(offset - 1)?.node?.elementType !in CrucibleTokenTypes.COMMENTS.types
    }
}
