package com.leyline.crucible.intellij

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.lang.LanguageBraceMatching
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.search.PsiTodoSearchHelper
import com.leyline.crucible.intellij.actions.CreateCrucibleFileAction
import com.leyline.crucible.intellij.editor.CrucibleBraceMatcher
import com.leyline.crucible.intellij.highlighting.CrucibleColorSettingsPage
import com.leyline.crucible.intellij.highlighting.CrucibleColors
import com.leyline.crucible.intellij.highlighting.CrucibleSyntaxHighlighter
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes
import com.leyline.crucible.intellij.psi.CrucibleFile

class CrucibleEditorTest : CrucibleTestCase() {
    fun `test cru files are Crucible files`() {
        assertSame(CrucibleFileType, FileTypeManager.getInstance().getFileTypeByExtension("cru"))
        val file = myFixture.configureByText("model.cru", "structure A {}")
        assertInstanceOf(file, CrucibleFile::class.java)
        assertInstanceOf(
            SyntaxHighlighterFactory.getSyntaxHighlighter(CrucibleLanguage, project, file.virtualFile),
            CrucibleSyntaxHighlighter::class.java,
        )
        assertInstanceOf(LanguageBraceMatching.INSTANCE.forLanguage(CrucibleLanguage), CrucibleBraceMatcher::class.java)
    }

    fun `test the highlighter colors keywords, types, annotations and literals`() {
        val highlighter = CrucibleSyntaxHighlighter()
        assertEquals(CrucibleColors.KEYWORD, highlighter.getTokenHighlights(CrucibleTokenTypes.KEYWORD).single())
        assertEquals(CrucibleColors.BUILTIN_TYPE, highlighter.getTokenHighlights(CrucibleTokenTypes.BUILTIN_TYPE).single())
        assertEquals(CrucibleColors.ANNOTATION, highlighter.getTokenHighlights(CrucibleTokenTypes.ANNOTATION_NAME).single())
        assertEquals(CrucibleColors.DOC_COMMENT, highlighter.getTokenHighlights(CrucibleTokenTypes.DOC_COMMENT).single())
        assertEmpty(highlighter.getTokenHighlights(CrucibleTokenTypes.IDENTIFIER))
    }

    fun `test the color page demo uses only known tags`() {
        val page = CrucibleColorSettingsPage()
        val tags = Regex("<(\\w+)>").findAll(page.demoText).map { it.groupValues[1] }.toSet()
        assertTrue(tags.isNotEmpty())
        assertTrue("unknown tags in ${tags - page.additionalHighlightingTagToDescriptorMap.keys}",
            page.additionalHighlightingTagToDescriptorMap.keys.containsAll(tags))
        assertEquals(24, page.attributeDescriptors.size)
    }

    fun `test line comments toggle`() {
        myFixture.configureByText("a.cru", "structure A {<caret>}\n")
        myFixture.testAction(CommentByLineCommentAction())
        myFixture.checkResult("// structure A {}\n")
    }

    fun `test enter inside a block indents one level`() {
        myFixture.configureByText("a.cru", "structure A {<caret>}\n")
        myFixture.type("\n")
        myFixture.checkResult("structure A {\n    <caret>\n}\n")
    }

    fun `test enter after a member keeps the block level`() {
        myFixture.configureByText("a.cru", "operation Op {\n    input {\n        a: string<caret>\n    }\n}\n")
        myFixture.type("\n")
        myFixture.checkResult("operation Op {\n    input {\n        a: string\n        <caret>\n    }\n}\n")
    }

    fun `test a closing brace typed first on a line moves to the block level`() {
        myFixture.configureByText("a.cru", "structure A {\n    a: string\n    <caret>\n")
        myFixture.type("}")
        myFixture.checkResult("structure A {\n    a: string\n}<caret>\n")
    }

    fun `test braces and quotes pair as you type`() {
        myFixture.configureByText("a.cru", "<caret>\n")
        myFixture.type("structure A {")
        myFixture.checkResult("structure A {<caret>}\n")
        myFixture.configureByText("b.cru", "string S { pattern <caret> }\n")
        myFixture.type("\"")
        myFixture.checkResult("string S { pattern \"<caret>\" }\n")
    }

    fun `test todo comments are found`() {
        val file = myFixture.configureByText("a.cru", "// TODO: name the errors\n/// FIXME document\nstructure A {}\n")
        assertEquals(2, PsiTodoSearchHelper.getInstance(project).findTodoItems(file).size)
    }

    fun `test file templates and code style are registered`() {
        val templates = FileTemplateManager.getDefaultInstance()
        for (name in listOf(CreateCrucibleFileAction.MODEL, CreateCrucibleFileAction.SERVICE, CreateCrucibleFileAction.MANIFEST)) {
            assertNotNull(name, templates.getInternalTemplate(name))
        }
        assertNotNull(LanguageCodeStyleSettingsProvider.forLanguage(CrucibleLanguage))
    }
}
