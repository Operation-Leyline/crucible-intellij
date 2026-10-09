package com.leyline.crucible.intellij

import com.intellij.psi.tree.IElementType
import com.leyline.crucible.intellij.lexer.CrucibleLexer
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes as T
import org.junit.Assert.assertEquals
import org.junit.Test

class CrucibleLexerTest {
    private fun lex(text: String): List<Pair<IElementType, String>> {
        val lexer = CrucibleLexer()
        lexer.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<IElementType, String>>()
        var expected = 0
        while (lexer.tokenType != null) {
            assertEquals("gap before ${lexer.tokenStart}", expected, lexer.tokenStart)
            check(lexer.tokenEnd > lexer.tokenStart) { "empty token at $expected" }
            out += lexer.tokenType!! to text.substring(lexer.tokenStart, lexer.tokenEnd)
            expected = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals("tokens must cover the text", text.length, expected)
        return out
    }

    private fun significant(text: String) = lex(text).filter { it.first != T.WHITE_SPACE }

    @Test
    fun `tokens cover any input, broken or not`() {
        for (text in listOf("", "\"unterminated", "@", "~ # 1..100 -> <- == != ..5 \"a\\\"b\" ////x\n", "a-", "-")) {
            lex(text)
        }
    }

    @Test
    fun `only unambiguous words are colored before the server answers`() {
        val tokens = significant(
            """
            /// Docs.
            // A note.
            @since("1")
            readonly operation GetCity reads City {
                http GET "/cities/{id}" -> 200
                input { status?: integer { range 1..100, format date-time } }
            }
            """.trimIndent(),
        )
        val kinds = tokens.associate { it.second to it.first }
        assertEquals(T.DOC_COMMENT, kinds["/// Docs."])
        assertEquals(T.LINE_COMMENT, kinds["// A note."])
        assertEquals(T.AT, kinds["@"])
        assertEquals(T.ANNOTATION_NAME, kinds["since"])
        assertEquals(T.KEYWORD, kinds["readonly"])
        assertEquals(T.KEYWORD, kinds["operation"])
        assertEquals(T.KEYWORD, kinds["GET"])
        // Contextual words and names wait for semantic tokens.
        assertEquals(T.IDENTIFIER, kinds["reads"])
        assertEquals(T.IDENTIFIER, kinds["GetCity"])
        assertEquals(T.IDENTIFIER, kinds["status"])
        assertEquals(T.IDENTIFIER, kinds["input"])
        assertEquals(T.BUILTIN_TYPE, kinds["integer"])
        assertEquals(T.STRING, kinds["\"/cities/{id}\""])
        assertEquals(T.OPERATOR, kinds["->"])
        assertEquals(T.NUMBER, kinds["200"])
        // Hyphenated words are one identifier, as in the compiler's lexer.
        assertEquals(T.IDENTIFIER, kinds["date-time"])
    }

    @Test
    fun `ranges are numbers around an operator`() {
        assertEquals(
            listOf(T.NUMBER to "1", T.OPERATOR to "..", T.NUMBER to "100"),
            significant("1..100").map { (kind, text) -> (if (text == "..") T.OPERATOR else kind) to text },
        )
        assertEquals(listOf(T.NUMBER to "-1.5e3"), significant("-1.5e3"))
    }
}
