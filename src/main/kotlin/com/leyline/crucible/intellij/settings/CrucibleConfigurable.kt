package com.leyline.crucible.intellij.settings

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
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
            row(CrucibleBundle.message("settings.server.arguments")) {
                expandableTextField()
                    .bindText(settings::serverArguments)
                    .align(AlignX.FILL)
            }
            row(CrucibleBundle.message("settings.server.environment")) {
                // Application settings: no project of their own to offer `.env` files from.
                cell(EnvironmentVariablesTextFieldWithBrowseButton(ProjectManager.getInstance().defaultProject))
                    .bind(
                        { field -> field.envs },
                        { field, environment -> field.envs = environment },
                        settings::serverEnvironment.toMutableProperty(),
                    )
                    .align(AlignX.FILL)
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
        group(CrucibleBundle.message("settings.cli.group")) {
            row(CrucibleBundle.message("settings.cli.path")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
                        .withTitle(CrucibleBundle.message("settings.cli.browse")),
                )
                    .bindText(settings::cliPath)
                    .align(AlignX.FILL)
                    .comment(CrucibleBundle.message("settings.cli.comment"))
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
