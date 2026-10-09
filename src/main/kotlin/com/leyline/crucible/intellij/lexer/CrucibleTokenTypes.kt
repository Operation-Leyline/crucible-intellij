package com.leyline.crucible.intellij.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.leyline.crucible.intellij.CrucibleLanguage

class CrucibleTokenType(debugName: String) : IElementType(debugName, CrucibleLanguage)

/** The highlighting lexer's tokens. The language server's semantic tokens refine them. */
object CrucibleTokenTypes {
    @JvmField val LINE_COMMENT = CrucibleTokenType("LINE_COMMENT")
    @JvmField val DOC_COMMENT = CrucibleTokenType("DOC_COMMENT")
    @JvmField val STRING = CrucibleTokenType("STRING")
    @JvmField val NUMBER = CrucibleTokenType("NUMBER")
    @JvmField val KEYWORD = CrucibleTokenType("KEYWORD")
    @JvmField val BUILTIN_TYPE = CrucibleTokenType("BUILTIN_TYPE")
    @JvmField val IDENTIFIER = CrucibleTokenType("IDENTIFIER")
    @JvmField val AT = CrucibleTokenType("@")
    @JvmField val ANNOTATION_NAME = CrucibleTokenType("ANNOTATION_NAME")
    @JvmField val LBRACE = CrucibleTokenType("{")
    @JvmField val RBRACE = CrucibleTokenType("}")
    @JvmField val LPAREN = CrucibleTokenType("(")
    @JvmField val RPAREN = CrucibleTokenType(")")
    @JvmField val LBRACKET = CrucibleTokenType("[")
    @JvmField val RBRACKET = CrucibleTokenType("]")
    @JvmField val OPERATOR = CrucibleTokenType("OPERATOR")
    @JvmField val COMMA = CrucibleTokenType(",")
    @JvmField val COLON = CrucibleTokenType(":")
    @JvmField val DOT = CrucibleTokenType(".")

    @JvmField val WHITE_SPACE: IElementType = TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, DOC_COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING)
    @JvmField val OPENING = TokenSet.create(LBRACE, LPAREN, LBRACKET)
    @JvmField val CLOSING = TokenSet.create(RBRACE, RPAREN, RBRACKET)
}
