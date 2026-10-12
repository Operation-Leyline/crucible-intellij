package com.leyline.crucible.intellij.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * The instant, stateless highlighting lexer. It mirrors the token rules of
 * Crucible's own lexer (`crucible-lang/src/lexer.rs`) and colors only what is
 * unambiguous: comments, strings, numbers, annotations, built-in types and
 * the words that are always keywords. Every other name waits for the language
 * server's semantic tokens, so colors are added when they arrive, never
 * switched.
 */
class CrucibleLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var end = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        end = endOffset
        tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = end

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= end) {
            tokenType = null
            return
        }
        var i = tokenStart
        val c = buffer[i]
        when {
            c == ' ' || c == '\t' || c == '\r' || c == '\n' -> {
                while (i < end && buffer[i].let { it == ' ' || it == '\t' || it == '\r' || it == '\n' }) i++
                finish(i, CrucibleTokenTypes.WHITE_SPACE)
            }
            c == '/' && peek(i + 1) == '/' -> {
                // `///` documents; `////` and longer are ordinary comments, as in Rust.
                val doc = peek(i + 2) == '/' && peek(i + 3) != '/'
                while (i < end && buffer[i] != '\n') i++
                finish(i, if (doc) CrucibleTokenTypes.DOC_COMMENT else CrucibleTokenTypes.LINE_COMMENT)
            }
            c == '"' -> {
                i++
                while (i < end && buffer[i] != '"' && buffer[i] != '\n') {
                    i += if (buffer[i] == '\\' && peek(i + 1) != '\n') 2 else 1
                }
                if (i < end && buffer[i] == '"') i++
                finish(i, CrucibleTokenTypes.STRING)
            }
            c.isAsciiDigit() || (c == '-' && peek(i + 1).isAsciiDigit()) -> finish(number(i), CrucibleTokenTypes.NUMBER)
            c == '-' && peek(i + 1) == '>' -> finish(i + 2, CrucibleTokenTypes.OPERATOR)
            c == '@' -> finish(i + 1, CrucibleTokenTypes.AT)
            isIdentStart(c) -> {
                i = identEnd(i)
                val word = buffer.subSequence(tokenStart, i).toString()
                val afterAt = tokenStart > 0 && buffer[tokenStart - 1] == '@'
                finish(i, if (afterAt) CrucibleTokenTypes.ANNOTATION_NAME else classify(word))
            }
            else -> finish(i + punctuationLength(i), punctuation(c))
        }
    }

    private fun number(start: Int): Int {
        var i = start
        if (buffer[i] == '-') i++
        while (i < end && buffer[i].isAsciiDigit()) i++
        // `1..5` is a range: a fraction needs a digit after the point.
        if (peek(i) == '.' && peek(i + 1).isAsciiDigit()) {
            i++
            while (i < end && buffer[i].isAsciiDigit()) i++
        }
        if (peek(i) == 'e' || peek(i) == 'E') {
            val sign = if (peek(i + 1) == '+' || peek(i + 1) == '-') 1 else 0
            if (peek(i + 1 + sign).isAsciiDigit()) {
                i += 1 + sign
                while (i < end && buffer[i].isAsciiDigit()) i++
            }
        }
        return i
    }

    private fun identEnd(start: Int): Int {
        var i = start + 1
        while (i < end) {
            val ch = buffer[i]
            i += when {
                ch.isAsciiLetterOrDigit() || ch == '_' -> 1
                // A hyphen joins two word characters (`date-time`); `a->b` is an arrow.
                ch == '-' && buffer[i - 1].isAsciiLetterOrDigit() && peek(i + 1).isAsciiLetterOrDigit() -> 1
                else -> return i
            }
        }
        return i
    }

    private fun punctuationLength(i: Int): Int {
        val pair = "${buffer[i]}${peek(i + 1)}"
        return if (pair in TWO_CHARACTER_OPERATORS) 2 else 1
    }

    private fun finish(tokenEnd: Int, type: IElementType) {
        this.tokenEnd = tokenEnd.coerceIn(tokenStart + 1, end)
        tokenType = type
    }

    private fun peek(index: Int): Char = if (index in 0 until end) buffer[index] else '\u0000'

    companion object {
        /** Words that are keywords wherever they appear. */
        @JvmField
        val KEYWORDS: Set<String> = setOf(
            "service", "protocol", "resource", "operation", "structure", "union", "error", "enum",
            "list", "map", "metadata", "rule", "except", "migration", "conversion", "template",
            "readonly", "immutable", "writeonly", "idempotent", "public", "internal", "strict", "sensitive",
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS",
        )

        @JvmField
        val BUILTIN_TYPES: Set<String> = setOf(
            "string", "boolean", "byte", "short", "integer", "long", "float", "double",
            "bigInteger", "bigDecimal", "timestamp", "blob", "document",
        )

        private val TWO_CHARACTER_OPERATORS = setOf("<-", "..", "==", "!=", "<=", ">=")

        fun classify(word: String): IElementType = when (word) {
            in KEYWORDS -> CrucibleTokenTypes.KEYWORD
            in BUILTIN_TYPES -> CrucibleTokenTypes.BUILTIN_TYPE
            else -> CrucibleTokenTypes.IDENTIFIER
        }

        private fun punctuation(c: Char): IElementType = when (c) {
            '{' -> CrucibleTokenTypes.LBRACE
            '}' -> CrucibleTokenTypes.RBRACE
            '(' -> CrucibleTokenTypes.LPAREN
            ')' -> CrucibleTokenTypes.RPAREN
            '[' -> CrucibleTokenTypes.LBRACKET
            ']' -> CrucibleTokenTypes.RBRACKET
            ',' -> CrucibleTokenTypes.COMMA
            ':' -> CrucibleTokenTypes.COLON
            '.' -> CrucibleTokenTypes.DOT
            ';', '?', '=', '<', '>', '!', '$' -> CrucibleTokenTypes.OPERATOR
            else -> CrucibleTokenTypes.BAD_CHARACTER
        }

        private fun isIdentStart(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

        private fun Char.isAsciiDigit() = this in '0'..'9'

        private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    }
}
