package com.leyline.crucible.intellij

import com.google.gson.JsonObject
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.ColorUtil
import com.intellij.testFramework.LightVirtualFile
import com.leyline.crucible.intellij.highlighting.CrucibleColorSettingsPage
import com.leyline.crucible.intellij.highlighting.CrucibleColors
import com.leyline.crucible.intellij.highlighting.CrucibleSemanticTokens
import com.leyline.crucible.intellij.lsp.CrucibleCodeLens
import com.leyline.crucible.intellij.lsp.CrucibleDocumentHighlights
import com.leyline.crucible.intellij.lsp.CrucibleFormatting
import com.leyline.crucible.intellij.lsp.CrucibleRename
import com.leyline.crucible.intellij.lsp.CrucibleGotoDeclarationHandler
import com.leyline.crucible.intellij.lsp.CrucibleLspClientDescriptor
import org.eclipse.lsp4j.Position

/** Typing assistance, templates and the LSP customizations. */
class CrucibleAssistTest : CrucibleTestCase() {
    fun `test enter in a doc comment continues it at its indentation`() {
        myFixture.configureByText("a.cru", "structure A {\n    /// The name.<caret>\n    name: string\n}\n")
        myFixture.type("\n")
        myFixture.checkResult("structure A {\n    /// The name.\n    /// <caret>\n    name: string\n}\n")
    }

    fun `test enter in the middle of a doc comment carries the rest over`() {
        myFixture.configureByText("a.cru", "/// First <caret>second\nstructure A {}\n")
        myFixture.type("\n")
        myFixture.checkResult("/// First \n/// <caret>second\nstructure A {}\n")
    }

    fun `test enter before a doc comment or in a plain comment does not continue it`() {
        myFixture.configureByText("a.cru", "<caret>/// Docs.\nstructure A {}\n")
        myFixture.type("\n")
        myFixture.checkResult("\n<caret>/// Docs.\nstructure A {}\n")
        myFixture.configureByText("b.cru", "// A note.<caret>\nstructure A {}\n")
        myFixture.type("\n")
        myFixture.checkResult("// A note.\n<caret>\nstructure A {}\n")
        myFixture.configureByText("c.cru", "//// Banner.<caret>\nstructure A {}\n")
        myFixture.type("\n")
        myFixture.checkResult("//// Banner.\n<caret>\nstructure A {}\n")
    }

    fun `test live templates are registered for Crucible files only`() {
        val settings = TemplateSettings.getInstance()
        for (name in listOf("structure", "union", "enum", "error", "resource", "operation", "service", "input", "output", "struct")) {
            assertNotNull(name, settings.getTemplate(name, "Crucible"))
        }
        myFixture.configureByText("a.cru", "<caret>\n")
        val here = TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.expanding(myFixture.file, myFixture.editor))
        assertTrue(here.any { it.key == "operation" })
        myFixture.configureByText("b.cru", "// <caret>\n")
        val inComment = TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.expanding(myFixture.file, myFixture.editor))
        assertFalse(inComment.any { it.groupName == "Crucible" })
        myFixture.configureByText("c.cru", "@since(\"1<caret>")
        val inOpenString = TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.expanding(myFixture.file, myFixture.editor))
        assertFalse(inOpenString.any { it.groupName == "Crucible" })
        myFixture.configureByText("d.cru", "@since(\"1\") <caret>")
        val afterString = TemplateManagerImpl.listApplicableTemplates(TemplateActionContext.expanding(myFixture.file, myFixture.editor))
        assertTrue(afterString.any { it.key == "operation" })
    }

    fun `test a template expands`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("a.cru", "<caret>\n")
        myFixture.type("structure\t")
        myFixture.type("Item")
        TemplateManagerImpl.getTemplateState(myFixture.editor)!!.gotoEnd(false)
        myFixture.checkResult("structure Item {\n    <caret>\n}\n")
    }

    fun `test the server colors, highlights and renames in cru files but not elsewhere`() {
        // The platform's defaults ask the server only in plain-text and
        // TextMate files; without these, a .cru file gets lexer colors only.
        val cru = myFixture.configureByText("a.cru", "structure A {}\n")
        assertTrue(CrucibleSemanticTokens().shouldAskServerForSemanticTokens(cru))
        assertTrue(CrucibleDocumentHighlights().shouldAskServerForDocumentHighlights(cru))
        assertTrue(CrucibleRename().shouldRunRename(cru))
        val other = myFixture.configureByText("notes.txt", "structure A {}\n")
        assertFalse(CrucibleSemanticTokens().shouldAskServerForSemanticTokens(other))
        assertFalse(CrucibleRename().shouldRunRename(other))
    }

    fun `test reformat of a cru file always goes to the server`() {
        val formatting = CrucibleFormatting()
        assertTrue(formatting.shouldFormatThisFileExclusivelyByServer(LightVirtualFile("a.cru"), true, false))
        assertFalse(formatting.shouldFormatThisFileExclusivelyByServer(LightVirtualFile("crucible.toml"), true, false))
    }

    fun `test go to declaration without a running server finds nothing and does not fail`() {
        myFixture.configureByText("a.cru", "structure A { b: <caret>B }\n")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNull(CrucibleGotoDeclarationHandler().getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor))
    }

    fun `test the client asks for usage lenses, whose command it runs`() {
        val options = CrucibleLspClientDescriptor(project).createInitializationOptions()
        assertEquals(mapOf("showReferences" to true), options)
    }

    fun `test a usage lens opens at the name even after a tab`() {
        val document = EditorFactory.getInstance().createDocument("structure\tShop {}\n")
        // The server's position of `Shop`: line 0, UTF-16 unit 10.
        val argument = JsonObject().apply {
            addProperty("line", 0)
            addProperty("character", 10)
        }
        val offset = CrucibleCodeLens.offsetFor(document, argument)!!
        assertEquals("Shop", document.text.substring(offset, offset + 4))
        assertNull(CrucibleCodeLens.offsetFor(document, "not a position"))
    }

    fun `test LSP positions count UTF-16 units, not editor columns`() {
        // A tab is one unit to the server and several columns to the editor;
        // an emoji is two units.
        val document = EditorFactory.getInstance().createDocument("a\tb😀c\nnext")
        assertEquals(5, CrucibleGotoDeclarationHandler.offsetOf(document, Position(0, 5)))
        assertEquals(Position(0, 5), CrucibleGotoDeclarationHandler.positionOf(document, 5))
        // Past the end of a line clamps to it; past the last line, to the last.
        assertEquals(6, CrucibleGotoDeclarationHandler.offsetOf(document, Position(0, 99)))
        assertEquals(document.textLength, CrucibleGotoDeclarationHandler.offsetOf(document, Position(9, 99)))
    }

    fun `test hover code fences named crucible or cru resolve to the plugin's language`() {
        // The platform's LSP hover looks a fence's tag up by language id or
        // display name (ignoring case), MIME type, then file extension.
        for (tag in listOf("crucible", "Crucible", "cru")) {
            assertSame(tag, CrucibleLanguage, QuickDocHighlightingHelper.guessLanguage(tag))
        }
    }

    fun `test a hover's signature and its code fences get the plugin's colors`() {
        // What the platform's LSP hover does with markdown that opens with a
        // fence: the fence becomes the signature, the rest the description.
        val signature = QuickDocHighlightingHelper.getStyledSignatureFragment(
            project,
            QuickDocHighlightingHelper.guessLanguage("crucible"),
            "structure Item",
        )
        assertColored(signature, "structure", CrucibleColors.KEYWORD)
        val description = DocMarkdownToHtmlConverter.convert(
            project,
            "An item.\n\n```crucible\nlist Items { member: string }\n```\n",
            null,
        )
        assertColored(description, "list", CrucibleColors.KEYWORD)
        assertColored(description, "string", CrucibleColors.BUILTIN_TYPE)
    }

    fun `test members have a color of their own on the settings page`() {
        val page = CrucibleColorSettingsPage()
        assertTrue(page.attributeDescriptors.any { it.key == CrucibleColors.MEMBER })
        assertEquals(CrucibleColors.MEMBER, page.additionalHighlightingTagToDescriptorMap["member"])
    }

    private fun assertColored(html: String, word: String, key: TextAttributesKey) {
        val color = EditorColorsManager.getInstance().globalScheme.getAttributes(key).foregroundColor
        assertNotNull(key.externalName, color)
        // A span carries the whitespace after its word, escaped.
        val styled = Regex("""<span style="[^"]*color:\s*#${ColorUtil.toHex(color)}[^"]*">$word(&#32;|\s)*</span>""", RegexOption.IGNORE_CASE)
        assertTrue("$word is not colored as ${key.externalName} in $html", styled.containsMatchIn(html))
    }
}
