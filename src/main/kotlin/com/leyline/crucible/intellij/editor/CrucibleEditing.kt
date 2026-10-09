package com.leyline.crucible.intellij.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes as T

/** Ctrl+/ comments lines with `//`; the language has no block comments. */
class CrucibleCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "// "

    override fun getBlockCommentPrefix(): String? = null

    override fun getBlockCommentSuffix(): String? = null

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}

/** Highlights matching brackets and closes `{`, `[` and `(` as you type. */
class CrucibleBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean =
        contextType == null ||
            contextType == T.WHITE_SPACE ||
            contextType in T.COMMENTS.types ||
            contextType in T.CLOSING.types ||
            contextType == T.COMMA

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(T.LBRACE, T.RBRACE, true),
            BracePair(T.LBRACKET, T.RBRACKET, false),
            BracePair(T.LPAREN, T.RPAREN, false),
        )
    }
}

/** Closes and steps over `"` as you type. */
class CrucibleQuoteHandler : SimpleTokenSetQuoteHandler(T.STRING)
