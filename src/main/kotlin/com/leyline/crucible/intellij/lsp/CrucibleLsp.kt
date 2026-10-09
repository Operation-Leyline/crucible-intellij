package com.leyline.crucible.intellij.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.CrucibleIcons
import com.leyline.crucible.intellij.highlighting.CrucibleSemanticTokens
import com.leyline.crucible.intellij.settings.CrucibleConfigurable
import java.nio.charset.StandardCharsets

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
        return GeneralCommandLine(server.path.toString())
            .withCharset(StandardCharsets.UTF_8)
            .apply { project.basePath?.let { withWorkDirectory(it) } }
    }

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer = CrucibleSemanticTokens()
    }
}
