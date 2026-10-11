package com.leyline.crucible.intellij.lsp

import com.google.gson.JsonObject
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsSupport
import com.intellij.platform.lsp.api.customization.LspFormattingCustomizer
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspRenameCustomizer
import com.intellij.platform.lsp.api.customization.LspRenameSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.psi.PsiFile
import com.intellij.util.execution.ParametersListUtil
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.CrucibleIcons
import com.leyline.crucible.intellij.highlighting.CrucibleSemanticTokens
import com.leyline.crucible.intellij.psi.CrucibleFile
import com.leyline.crucible.intellij.settings.CrucibleConfigurable
import com.leyline.crucible.intellij.settings.CrucibleSettings
import java.awt.event.MouseEvent
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position

/** `.cru` files and package manifests, which the server checks too. */
internal fun isCrucibleFile(file: VirtualFile): Boolean =
    file.extension == "cru" || file.name == "crucible.toml"

/** Starts one `crucible-lsp` per project when a Crucible file opens. */
class CrucibleLspIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
        if (!isCrucibleFile(file)) return
        if (CrucibleServerLocator.find() == null) {
            warnMissing(project)
            return
        }
        clientStarter.ensureClientStarted(CrucibleLspClientDescriptor(project))
    }

    /** The status bar's language server widget: the Crucible icon and a link to its settings. */
    override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
        LspClientWidgetItem(lspClient, currentFile, CrucibleIcons.FILE, CrucibleConfigurable::class.java)

    companion object {
        private val WARNED = Key.create<Boolean>("crucible.lsp.missing.warned")

        /** Restarts the server in every open project, after the settings change. */
        fun restartAll() {
            for (project in ProjectManager.getInstance().openProjects) {
                project.putUserData(WARNED, null)
                LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(CrucibleLspIntegrationProvider::class.java)
            }
        }

        private fun warnMissing(project: Project) {
            if (project.getUserData(WARNED) == true) return
            project.putUserData(WARNED, true)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Crucible")
                .createNotification(
                    CrucibleBundle.message("notification.server.missing.title"),
                    CrucibleBundle.message("notification.server.missing.content"),
                    NotificationType.WARNING,
                )
                .addAction(
                    NotificationAction.createSimpleExpiring(CrucibleBundle.message("notification.server.missing.configure")) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, CrucibleConfigurable::class.java)
                    },
                )
                .notify(project)
        }
    }
}

class CrucibleLspClientDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Crucible") {
    override fun isSupportedFile(file: VirtualFile): Boolean = isCrucibleFile(file)

    override fun createCommandLine(): GeneralCommandLine {
        val server = CrucibleServerLocator.find()
            ?: throw ExecutionException(CrucibleBundle.message("notification.server.missing.title"))
        val settings = CrucibleSettings.getInstance()
        return commandLine(server.path, settings.serverArguments, settings.serverEnvironment, project.basePath)
    }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer = CrucibleSemanticTokens()
        override val formattingCustomizer: LspFormattingCustomizer = CrucibleFormatting()
        override val codeLensCustomizer: LspCodeLensCustomizer = CrucibleCodeLens()
        override val documentHighlightsCustomizer: LspDocumentHighlightsCustomizer = CrucibleDocumentHighlights()
        override val renameCustomizer: LspRenameCustomizer = CrucibleRename()
    }

    companion object {
        /** The server's command line with the user's extra arguments and environment. */
        fun commandLine(server: Path, arguments: String, environment: Map<String, String>, workDirectory: String?): GeneralCommandLine =
            GeneralCommandLine(server.toString())
                .withParameters(ParametersListUtil.parse(arguments))
                .withEnvironment(environment)
                .withCharset(StandardCharsets.UTF_8)
                .apply { workDirectory?.let { withWorkDirectory(it) } }
    }
}

/**
 * Reformat Code on a `.cru` file always runs the compiler's formatter. The
 * platform's default sends it to the server only when the IDE cannot format
 * the file itself and the server explicitly asks to; for `.cru` the server's
 * formatter is the only one that knows the language. `crucible.toml` stays
 * with the TOML plugin's formatter.
 */
class CrucibleFormatting : LspFormattingSupport() {
    override fun shouldFormatThisFileExclusivelyByServer(
        file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean,
    ): Boolean = file.extension == "cru"
}

/**
 * Highlighting other uses of the name under the caret. Like semantic tokens,
 * the platform asks the server only in plain-text and TextMate files unless
 * told otherwise; the flat Crucible PSI knows no uses of its own.
 */
class CrucibleDocumentHighlights : LspDocumentHighlightsSupport() {
    override fun shouldAskServerForDocumentHighlights(psiFile: PsiFile): Boolean = psiFile is CrucibleFile
}

/**
 * Rename (Shift+F6) through the server, which renames a declaration across
 * the package. By default the platform leaves rename in other languages to
 * the IDE, which would only rename the text under the caret here.
 */
class CrucibleRename : LspRenameSupport() {
    override fun shouldRunRename(psiFile: PsiFile): Boolean = psiFile is CrucibleFile
}

/**
 * The server's "N usages" lenses carry `crucible.showReferences` with the
 * declaration's position; clicking one shows its usages as Ctrl+click on the
 * name would, instead of asking the server to run a command it does not have.
 */
class CrucibleCodeLens : LspCodeLensSupport() {
    override fun codeLensClicked(lspClient: LspClient, contextFile: VirtualFile, command: Command, mouseEvent: MouseEvent?) {
        if (command.command != SHOW_REFERENCES) {
            super.codeLensClicked(lspClient, contextFile, command, mouseEvent)
            return
        }
        val (line, character) = positionOf(command.arguments?.getOrNull(1)) ?: return
        val project = lspClient.project
        // The server counts UTF-16 units; a descriptor's column counts a tab
        // as several, so go through the document's offset.
        val document = FileDocumentManager.getInstance().getDocument(contextFile) ?: return
        val offset = CrucibleGotoDeclarationHandler.offsetOf(document, Position(line, character))
        val editor = FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, contextFile, offset), true) ?: return
        val action = ActionManager.getInstance().getAction(SHOW_USAGES_ACTION) ?: return
        ActionManager.getInstance().tryToExecute(action, mouseEvent, editor.contentComponent, ActionPlaces.EDITOR_INLAY, true)
    }

    companion object {
        const val SHOW_REFERENCES = "crucible.showReferences"

        /** The platform's Show Usages popup (Ctrl+Alt+F7). */
        private const val SHOW_USAGES_ACTION = "ShowUsages"

        /** An LSP `Position` argument, as lsp4j hands it over: a JSON object. */
        fun positionOf(argument: Any?): Pair<Int, Int>? {
            val json = argument as? JsonObject ?: return null
            val line = json.get("line")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
            val character = json.get("character")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
            return line to character
        }
    }
}
