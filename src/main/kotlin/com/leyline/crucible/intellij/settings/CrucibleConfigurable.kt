package com.leyline.crucible.intellij.settings

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.lsp.CrucibleLspIntegrationProvider
import com.leyline.crucible.intellij.lsp.CrucibleServerLocator
import com.leyline.crucible.intellij.lsp.ResolvedServer

/** Settings | Languages & Frameworks | Crucible. */
class CrucibleConfigurable : BoundSearchableConfigurable(
    CrucibleBundle.message("settings.display.name"),
    "reference.settings.crucible",
    "crucible.settings",
) {
    private val settings = CrucibleSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        group(CrucibleBundle.message("settings.server.group")) {
            lateinit var custom: JBCheckBox
            row {
                custom = checkBox(CrucibleBundle.message("settings.server.custom"))
                    .bindSelected(settings::useCustomServer)
                    .component
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
                        .withTitle(CrucibleBundle.message("settings.server.custom.browse")),
                )
                    .bindText(settings::serverPath)
                    .align(AlignX.FILL)
                    .enabledIf(custom.selected)
            }
            row {
                comment(CrucibleBundle.message("settings.server.automatic.comment"))
            }
            row {
                val status = comment("").component
                // Resolving runs `crucible-lsp --version`: keep it off the UI thread.
                ApplicationManager.getApplication().executeOnPooledThread {
                    val text = describe(CrucibleServerLocator.find())
                    ApplicationManager.getApplication().invokeLater({ status.text = text }, ModalityState.any())
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        CrucibleLspIntegrationProvider.restartAll()
    }

    private fun describe(server: ResolvedServer?): String {
        if (server == null) return CrucibleBundle.message("settings.server.resolved.none")
        val version = runCatching {
            ExecUtil.execAndGetOutput(GeneralCommandLine(server.path.toString(), "--version"), 3_000)
                .stdout.trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
        val where = "${server.path} [${server.source.name.lowercase()}]"
        return if (version == null) {
            CrucibleBundle.message("settings.server.resolved", where)
        } else {
            CrucibleBundle.message("settings.server.resolved.version", where, version)
        }
    }
}
