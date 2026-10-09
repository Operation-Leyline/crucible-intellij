package com.leyline.crucible.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.CrucibleIcons

/** New | Crucible File: a model file, a service skeleton, or a package manifest. */
class CreateCrucibleFileAction : CreateFileFromTemplateAction(), DumbAware {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(CrucibleBundle.message("action.new.file.title"))
            .addKind(CrucibleBundle.message("action.new.file.kind.model"), CrucibleIcons.FILE, MODEL)
            .addKind(CrucibleBundle.message("action.new.file.kind.service"), CrucibleIcons.FILE, SERVICE)
            .addKind(CrucibleBundle.message("action.new.file.kind.manifest"), AllIcons.FileTypes.Config, MANIFEST)
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        CrucibleBundle.message("action.new.file.title")

    override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? =
        // A manifest is always crucible.toml, whatever name was typed.
        super.createFileFromTemplate(if (template.name == MANIFEST) "crucible" else name, template, dir)

    companion object {
        const val MODEL = "Crucible File"
        const val SERVICE = "Crucible Service"
        const val MANIFEST = "Crucible Manifest"
    }
}
