package com.leyline.crucible.intellij.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.leyline.crucible.intellij.CrucibleLanguage
import com.leyline.crucible.intellij.lexer.CrucibleLexer
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes

/**
 * Indentation by bracket depth, the rule Crucible's formatter uses: Enter
 * inside a block indents one level, and a line that starts by closing a block
 * sits at the block's level.
 */
object CrucibleIndent {
    /** The bracket depth at [offset]: brackets opened before it and not yet closed. */
    fun depth(text: CharSequence, offset: Int): Int {
        val lexer = CrucibleLexer()
        lexer.start(text, 0, offset.coerceAtMost(text.length), 0)
        var depth = 0
        while (lexer.tokenType != null) {
            when (lexer.tokenType) {
                in CrucibleTokenTypes.OPENING.types -> depth++
                in CrucibleTokenTypes.CLOSING.types -> depth = (depth - 1).coerceAtLeast(0)
            }
            lexer.advance()
        }
        return depth
    }

    /** The indentation for a line whose text starts at [lineStart]. */
    fun lineIndent(text: CharSequence, lineStart: Int, options: IndentOptions): String {
        var first = lineStart
        while (first < text.length && (text[first] == ' ' || text[first] == '\t')) first++
        val closes = first < text.length && text[first] in "}])"
        val level = (depth(text, lineStart) - if (closes) 1 else 0).coerceAtLeast(0)
        val unit = if (options.USE_TAB_CHARACTER) "\t" else " ".repeat(options.INDENT_SIZE)
        return unit.repeat(level)
    }
}

class CrucibleLineIndentProvider : LineIndentProvider {
    override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
        val document = editor.document
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        return CrucibleIndent.lineIndent(document.charsSequence, lineStart, indentOptions(project, document))
    }

    override fun isSuitableFor(language: Language?): Boolean = language == CrucibleLanguage
}

/** Typing a closing bracket first on a line moves the line to its block's level. */
class CrucibleTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.language != CrucibleLanguage || c !in "}])") return Result.CONTINUE
        val document = editor.document
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        val lineStart = document.getLineStartOffset(line)
        val before = document.charsSequence.subSequence(lineStart, caret - 1)
        if (before.isNotBlank()) return Result.CONTINUE
        val indent = CrucibleIndent.lineIndent(document.charsSequence, lineStart, indentOptions(project, document))
        if (indent != before.toString()) {
            document.replaceString(lineStart, caret - 1, indent)
            editor.caretModel.moveToOffset(lineStart + indent.length + 1)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        return Result.STOP
    }
}

private fun indentOptions(project: Project, document: Document): IndentOptions =
    CodeStyle.getIndentOptions(project, document)
