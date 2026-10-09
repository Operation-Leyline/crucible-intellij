package com.leyline.crucible.intellij.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.leyline.crucible.intellij.lexer.CrucibleLexer
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes as T

class CrucibleSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = CrucibleLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(COLORS[tokenType])

    companion object {
        private val COLORS: Map<IElementType, TextAttributesKey> = mapOf(
            T.KEYWORD to CrucibleColors.KEYWORD,
            T.BUILTIN_TYPE to CrucibleColors.BUILTIN_TYPE,
            T.AT to CrucibleColors.ANNOTATION,
            T.ANNOTATION_NAME to CrucibleColors.ANNOTATION,
            T.STRING to CrucibleColors.STRING,
            T.NUMBER to CrucibleColors.NUMBER,
            T.LINE_COMMENT to CrucibleColors.LINE_COMMENT,
            T.DOC_COMMENT to CrucibleColors.DOC_COMMENT,
            T.LBRACE to CrucibleColors.BRACES,
            T.RBRACE to CrucibleColors.BRACES,
            T.LBRACKET to CrucibleColors.BRACKETS,
            T.RBRACKET to CrucibleColors.BRACKETS,
            T.LPAREN to CrucibleColors.PARENTHESES,
            T.RPAREN to CrucibleColors.PARENTHESES,
            T.OPERATOR to CrucibleColors.OPERATOR,
            T.COMMA to CrucibleColors.PUNCTUATION,
            T.COLON to CrucibleColors.PUNCTUATION,
            T.DOT to CrucibleColors.PUNCTUATION,
            T.BAD_CHARACTER to HighlighterColors.BAD_CHARACTER,
        )
    }
}

class CrucibleSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        CrucibleSyntaxHighlighter()
}
