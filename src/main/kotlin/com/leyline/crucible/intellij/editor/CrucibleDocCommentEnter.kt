package com.leyline.crucible.intellij.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.leyline.crucible.intellij.psi.CrucibleFile

/**
 * Enter inside a `///` doc comment starts the next line with `///` at the
 * comment's indentation, so a paragraph of documentation reads as one
 * comment. Enter before the `///`, or in a `//` or `////` comment, splits
 * the line as usual.
 */
class CrucibleDocCommentEnter : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
        if (file !is CrucibleFile) return Result.Continue
        val document = editor.document
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        if (line == 0) return Result.Continue
        // What was left of the caret is now the previous line: it holds the
        // `///` only when the caret was after it.
        val previous = document.getText(TextRange(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1)))
        val indent = previous.takeWhile { it == ' ' || it == '\t' }
        // `////` is an ordinary comment, as the lexer has it.
        if (!previous.startsWith(DOC, indent.length) || previous.startsWith("$DOC/", indent.length)) return Result.Continue
        val start = document.getLineStartOffset(line)
        val text = document.charsSequence
        var end = start
        while (end < document.getLineEndOffset(line) && (text[end] == ' ' || text[end] == '\t')) end++
        // The line the caret split off may already be a doc line.
        val prefix = if (text.startsWith(DOC, end)) indent else "$indent$DOC "
        document.replaceString(start, end, prefix)
        editor.caretModel.moveToOffset(start + prefix.length)
        return Result.Stop
    }

    private companion object {
        const val DOC = "///"
    }
}
