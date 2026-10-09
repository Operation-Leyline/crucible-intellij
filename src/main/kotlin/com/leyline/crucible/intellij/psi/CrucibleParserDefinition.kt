package com.leyline.crucible.intellij.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.leyline.crucible.intellij.CrucibleFileType
import com.leyline.crucible.intellij.CrucibleLanguage
import com.leyline.crucible.intellij.lexer.CrucibleLexer
import com.leyline.crucible.intellij.lexer.CrucibleTokenTypes

class CrucibleFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CrucibleLanguage) {
    override fun getFileType(): FileType = CrucibleFileType

    override fun toString(): String = "Crucible file"
}

/**
 * A flat tree of tokens. The language is parsed, checked and navigated by the
 * language server, which runs Crucible's own compiler; duplicating its grammar
 * here would only let the two disagree. Tokens are enough for highlighting,
 * commenting, brace matching, TODOs and spell checking.
 */
class CrucibleParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = CrucibleLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = CrucibleTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = CrucibleTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = CrucibleFile(viewProvider)

    companion object {
        @JvmField
        val FILE = IFileElementType(CrucibleLanguage)
    }
}
