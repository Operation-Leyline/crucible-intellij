package com.leyline.crucible.intellij.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selectedValueMatches
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.settings.CrucibleConfigurable
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JTextField

/** The Crucible submenu: hidden, not empty, outside a Crucible package. */
class CrucibleToolsGroup : DefaultActionGroup() {
    init {
        templatePresentation.isHideGroupIfEmpty = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** What the CLI actions share: the selected package, the CLI, and a console to run in. */
abstract class CrucibleCliAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && packageOf(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val packageDir = packageOf(event) ?: return
        val cli = CrucibleCli.find() ?: return warnMissing(project)
        perform(project, cli, packageDir)
    }

    protected abstract fun perform(project: Project, cli: Path, packageDir: Path)

    /** Runs [commandLine] in the Run tool window, refreshing [refresh] once it ends. */
    protected fun run(project: Project, title: String, commandLine: GeneralCommandLine, refresh: Path? = null) {
        val handler = try {
            ColoredProcessHandler(commandLine)
        } catch (error: ExecutionException) {
            Messages.showErrorDialog(project, error.message, title)
            return
        }
        ProcessTerminatedListener.attach(handler)
        RunContentExecutor(project, handler)
            .withTitle(title)
            .withRerun { run(project, title, commandLine, refresh) }
            .withAfterCompletion {
                refresh?.let { LocalFileSystem.getInstance().refreshNioFiles(listOf(it), true, true, null) }
            }
            .run()
    }

    private fun packageOf(event: AnActionEvent): Path? {
        val file: VirtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        if (!file.isInLocalFileSystem) return null
        return CruciblePackages.packageOf(file.toNioPath())
    }

    private fun warnMissing(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Crucible")
            .createNotification(
                CrucibleBundle.message("notification.cli.missing.title"),
                CrucibleBundle.message("notification.cli.missing.content"),
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

/** Tools | Crucible | Check Crucible Package: `crucible check` on the package. */
class CheckCruciblePackageAction : CrucibleCliAction() {
    override fun perform(project: Project, cli: Path, packageDir: Path) {
        run(project, CrucibleBundle.message("cli.check.title", packageDir.fileName), CrucibleCli.check(cli, packageDir))
    }
}

/** Tools | Crucible | Generate Crucible Client: a client package from `crucible generate`. */
class GenerateCrucibleClientAction : CrucibleCliAction() {
    override fun perform(project: Project, cli: Path, packageDir: Path) {
        val dialog = GenerateClientDialog(project, packageDir)
        if (!dialog.showAndGet()) return
        val target = dialog.target
        // The CLI runs in the package directory; resolve here too, so the
        // directory refreshed afterwards is the one written.
        val out = packageDir.resolve(dialog.outDirectory.trim()).normalize()
        run(
            project,
            CrucibleBundle.message("cli.generate.title", target.id),
            CrucibleCli.generate(cli, packageDir, target, out, dialog.options),
            refresh = out,
        )
    }
}

/**
 * The target, the output directory and the options that target requires,
 * prefilled from the package namespace. Only the chosen target's options show.
 */
class GenerateClientDialog(project: Project, packageDir: Path) : DialogWrapper(project) {
    private val namespace = CruciblePackages.namespace(packageDir) ?: packageDir.fileName.toString()
    private val outBase = (project.basePath?.let { Path.of(it) } ?: packageDir).resolve("generated")
    private val targets = ComboBox(GenerateTarget.entries.toTypedArray())
    private val fields = GenerateOption.entries.associateWith { JBTextField() }
    private var outField: JTextField? = null

    var target: GenerateTarget = GenerateTarget.TYPESCRIPT
        private set
    var outDirectory: String = ""
    val options: Map<GenerateOption, String>
        get() = target.options.associateWith { fields.getValue(it).text.trim() }

    init {
        title = CrucibleBundle.message("cli.generate.dialog.title")
        targets.addActionListener { select(targets.item) }
        select(target)
        init()
    }

    /** A new target gets its own suggestions; values typed for the previous one would not fit. */
    private fun select(selected: GenerateTarget) {
        target = selected
        for ((option, value) in selected.defaults(namespace)) fields.getValue(option).text = value
        outDirectory = outBase.resolve("${selected.id}-client").toString()
        outField?.text = outDirectory
    }

    override fun createCenterPanel(): JComponent = panel {
        row(CrucibleBundle.message("cli.generate.target")) { cell(targets) }
        row(CrucibleBundle.message("cli.generate.out")) {
            outField = textFieldWithBrowseButton(
                FileChooserDescriptorFactory.singleDir().withTitle(CrucibleBundle.message("cli.generate.out.browse")),
            )
                .bindText(::outDirectory)
                .align(AlignX.FILL)
                .validationOnApply { if (it.text.isBlank()) error(CrucibleBundle.message("cli.generate.required")) else null }
                .component.textField
        }
        for ((option, field) in fields) {
            row(option.label) {
                cell(field)
                    .align(AlignX.FILL)
                    .validationOnApply {
                        if (option in target.options && it.text.isBlank()) error(CrucibleBundle.message("cli.generate.required")) else null
                    }
            }.visibleIf(targets.selectedValueMatches { it != null && option in it.options })
        }
    }
}
