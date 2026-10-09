package com.leyline.crucible.intellij.editor

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.leyline.crucible.intellij.lexer.CrucibleLexer
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes
import com.leyline.crucible.intellij.psi.CrucibleFile

/** Finds TODO and FIXME in comments, for highlighting and the TODO tool window. */
class CrucibleIndexPatternBuilder : IndexPatternBuilder {
    override fun getIndexingLexer(file: PsiFile): Lexer? = if (file is CrucibleFile) CrucibleLexer() else null

    override fun getCommentTokenSet(file: PsiFile): TokenSet? =
        if (file is CrucibleFile) CrucibleTokenTypes.COMMENTS else null

    override fun getCommentStartDelta(tokenType: IElementType?): Int =
        if (tokenType == CrucibleTokenTypes.DOC_COMMENT) 3 else 2

    override fun getCommentEndDelta(tokenType: IElementType?): Int = 0
}
