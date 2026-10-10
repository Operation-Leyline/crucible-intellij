package com.leyline.crucible.intellij.lsp

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Position

/**
 * Go to Declaration, and the underline Ctrl+hover draws over a name that has
 * one. The platform's LSP client resolves a name only while the Go to
 * Declaration action runs, so Ctrl+hover, which asks first, found nothing.
 * Declaration handlers are asked before the platform's references, so this
 * one answers both, from the server's `textDocument/definition`.
 */
class CrucibleGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val source = sourceElement ?: return null
        if (source.node?.elementType !in NAMES) return null
        val file = source.containingFile?.virtualFile ?: return null
        if (!isCrucibleFile(file)) return null
        val project = source.project
        val client = LspClientManager.getInstance(project)
            .getClients(CrucibleLspIntegrationProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running } ?: return null
        val document = editor?.document ?: FileDocumentManager.getInstance().getDocument(file) ?: return null
        val targets = definitions(client, file, positionOf(document, offset))
            .mapNotNull { (uri, position) -> elementAt(client, uri, position, project) }
            // On the declaration itself the server answers with the name; leave
            // that to the platform, which then shows the usages.
            .filterNot { it.containingFile?.virtualFile == file && source.textRange.contains(it.textRange.startOffset) }
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun definitions(client: LspClient, file: VirtualFile, position: Position): List<Pair<String, Position>> {
        val params = DefinitionParams(client.getDocumentIdentifier(file), position)
        // A stopped or busy server must never hold up the editor: no answer is no target.
        val result = runCatching {
            client.sendRequestSync(TIMEOUT_MS) { it.textDocumentService.definition(params) }
        }.getOrNull() ?: return emptyList()
        return if (result.isLeft) {
            result.left.orEmpty().map { it.uri to it.range.start }
        } else {
            result.right.orEmpty().map { it.targetUri to (it.targetSelectionRange ?: it.targetRange).start }
        }
    }

    private fun elementAt(client: LspClient, uri: String, position: Position, project: Project): PsiElement? {
        val target = client.descriptor.findFileByUri(uri) ?: return null
        val document = FileDocumentManager.getInstance().getDocument(target) ?: return null
        val psi = PsiManager.getInstance(project).findFile(target) ?: return null
        return psi.findElementAt(offsetOf(document, position)) ?: psi
    }

    companion object {
        /** Ctrl+hover asks on every move; an answer later than this is no answer. */
        private const val TIMEOUT_MS = 1_000

        /** Tokens that can name a declaration: names, and the names after `@`. */
        private val NAMES = setOf(CrucibleTokenTypes.IDENTIFIER, CrucibleTokenTypes.ANNOTATION_NAME)

        /** LSP positions count UTF-16 code units, as Java strings do. */
        fun positionOf(document: Document, offset: Int): Position {
            val line = document.getLineNumber(offset.coerceIn(0, document.textLength))
            return Position(line, offset - document.getLineStartOffset(line))
        }

        fun offsetOf(document: Document, position: Position): Int {
            if (document.lineCount == 0) return 0
            val line = position.line.coerceIn(0, document.lineCount - 1)
            return (document.getLineStartOffset(line) + position.character).coerceAtMost(document.getLineEndOffset(line))
        }
    }
}
