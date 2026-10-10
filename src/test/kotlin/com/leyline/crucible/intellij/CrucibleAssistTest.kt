package com.leyline.crucible.intellij

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.leyline.crucible.intellij.json.CrucibleManifestSchema
import com.leyline.crucible.intellij.lsp.CrucibleFormatting
import com.leyline.crucible.intellij.lsp.CrucibleGotoDeclarationHandler

/** Typing assistance, templates, the manifest schema and the LSP customizations. */
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

    fun `test crucible toml gets the manifest schema`() {
        val file = myFixture.configureByText("crucible.toml", "[model]\n<caret>\n")
        val schemas = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file.virtualFile)
        assertTrue("schemas: $schemas", schemas.any { it == CrucibleManifestSchema.schemaFile })
        assertNotNull(JsonSchemaService.Impl.get(project).getSchemaObject(file))
        assertFalse(CrucibleManifestSchema.isAvailable(LightVirtualFile("Cargo.toml")))
    }

    fun `test manifest keys complete from the schema`() {
        myFixture.configureByText("crucible.toml", "[model]\n<caret>\n")
        myFixture.completeBasic()
        val model = myFixture.lookupElementStrings.orEmpty()
        assertTrue("completions: $model", model.containsAll(listOf("namespace", "sources", "imports")))
        myFixture.configureByText("crucible.toml", "schema_version = 1\n\n[rules.naming]\n<caret>\n")
        myFixture.completeBasic()
        val naming = myFixture.lookupElementStrings.orEmpty()
        assertTrue("completions: $naming", naming.containsAll(listOf("severity", "operations", "members")))
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
}
