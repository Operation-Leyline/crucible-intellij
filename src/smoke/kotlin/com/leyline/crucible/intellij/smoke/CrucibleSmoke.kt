package com.leyline.crucible.intellij.smoke

import com.intellij.codeInsight.daemon.impl.MainPassesRunner
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiManager
import com.leyline.crucible.intellij.CrucibleFileType
import com.leyline.crucible.intellij.CrucibleLanguage
import com.leyline.crucible.intellij.highlighting.CrucibleSyntaxHighlighter
import com.leyline.crucible.intellij.lsp.CrucibleLspIntegrationProvider
import com.leyline.crucible.intellij.lsp.CrucibleServerLocator
import com.leyline.crucible.intellij.lsp.ServerSource
import com.leyline.crucible.intellij.psi.CrucibleFile
import java.nio.file.Path
import kotlin.system.exitProcess
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SemanticTokensParams

/**
 * A test-only application starter, never in the distributed plugin. It opens
 * a real project in a headless IDE and checks the plugin end to end through
 * the IDE's own LSP client and highlighting passes.
 */
class CrucibleSmoke : ApplicationStarter {
    override val isHeadless: Boolean = true

    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        try {
            run(Path.of(args[1]))
            exitProcess(0)
        } catch (error: Throwable) {
            error.printStackTrace()
            exitProcess(1)
        }
    }

    private fun run(root: Path) {
        val project = ProjectManager.getInstance().loadAndOpenProject(root.toString()) ?: fail("the project did not open")
        DumbService.getInstance(project).waitForSmartMode()
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve("api/service.cru"))
            ?: fail("api/service.cru is missing")

        check(FileTypeManager.getInstance().getFileTypeByFile(file) == CrucibleFileType) { ".cru is not a Crucible file" }
        ApplicationManager.getApplication().runReadAction {
            check(PsiManager.getInstance(project).findFile(file) is CrucibleFile) { "no Crucible PSI" }
            check(SyntaxHighlighterFactory.getSyntaxHighlighter(CrucibleLanguage, project, file) is CrucibleSyntaxHighlighter) {
                "no highlighter"
            }
        }
        report("file type, PSI and highlighter registered")

        val server = CrucibleServerLocator.find() ?: fail("no crucible-lsp found")
        if (System.getProperty("crucible.smoke.expectBundled") == "true") {
            check(server.source == ServerSource.BUNDLED) { "expected the bundled server, found ${server.path} (${server.source})" }
        }
        report("server: ${server.path} (${server.source.name.lowercase()})")

        ApplicationManager.getApplication().invokeAndWait { FileEditorManager.getInstance(project).openFile(file, true) }
        LspClientManager.getInstance(project).startClientsIfNeeded(CrucibleLspIntegrationProvider::class.java)
        val client = awaitClient(project)
        report("crucible-lsp running through the platform LSP client")

        // Diagnostics arrive asynchronously once the IDE has sent the document;
        // they also prove the server compiled the whole package.
        var errors = emptyList<String>()
        val deadline = System.nanoTime() + 60_000_000_000L
        while (System.nanoTime() < deadline) {
            errors = errors(project, file)
            if (errors.any { "unknown type `ItemIdd`" in it }) break
            Thread.sleep(500)
        }
        check(errors.any { "unknown type `ItemIdd`" in it }) { "editor errors: $errors" }
        report("the editor shows the compiler's error $errors")

        val text = String(file.contentsToByteArray(), file.charset)
        val document = client.getDocumentIdentifier(file)
        val typeAt = position(text, "other: ", 0).let { Position(it.line, it.character + "other: ".length) }
        val hover = client.sendRequestSync(10_000) { it.textDocumentService.hover(HoverParams(document, typeAt)) }
        check(hover?.contents?.right?.value?.contains("string ItemId") == true) { "hover: $hover" }
        val tokens = client.sendRequestSync(10_000) { it.textDocumentService.semanticTokensFull(SemanticTokensParams(document)) }
        check((tokens?.data?.size ?: 0) > 50) { "semantic tokens: $tokens" }
        val completion = client.sendRequestSync(10_000) { it.textDocumentService.completion(CompletionParams(document, typeAt)) }
        val labels = completion?.left?.map { it.label }.orEmpty()
        check("ItemId" in labels && "string" in labels) { "completion: $labels" }
        println("CRUCIBLE_SMOKE_OK: hover, semantic tokens and completion answer through the IDE's client")
    }
    private fun awaitClient(project: Project): LspClient {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (System.nanoTime() < deadline) {
            for (client in LspClientManager.getInstance(project).getClients(CrucibleLspIntegrationProvider::class.java)) {
                when (client.state) {
                    LspServerState.Running -> return client
                    LspServerState.ShutdownUnexpectedly -> fail("crucible-lsp exited unexpectedly")
                    else -> {}
                }
            }
            Thread.sleep(200)
        }
        fail("crucible-lsp did not start")
    }

    /** The errors the daemon's main passes produce, as an open editor shows them. */
    private fun errors(project: Project, file: VirtualFile): List<String> {
        var result = emptyList<String>()
        ApplicationManager.getApplication().invokeAndWait {
            result = MainPassesRunner(project, "Crucible smoke", null)
                .runMainPasses(listOf(file), HighlightSeverity.ERROR)
                .values.flatten()
                .filter { it.severity == HighlightSeverity.ERROR }
                .mapNotNull { it.description }
        }
        return result
    }

    private fun position(text: String, word: String, nth: Int): Position {
        var offset = -1
        repeat(nth + 1) { offset = text.indexOf(word, offset + 1) }
        check(offset >= 0) { "no $word" }
        val line = text.substring(0, offset).count { it == '\n' }
        return Position(line, offset - (text.lastIndexOf('\n', offset - 1) + 1))
    }

    private fun report(message: String) = println("CRUCIBLE_SMOKE: $message")

    private fun fail(message: String): Nothing = throw AssertionError(message)
}
