package com.leyline.crucible.intellij.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.psi.PsiFile
import com.leyline.crucible.intellij.psi.CrucibleFile

/**
 * Paints the server's semantic tokens (`TOKEN_TYPES` and `TOKEN_MODIFIERS` in
 * crucible-lsp/src/analysis.rs) with the plugin's own colors, so they match
 * the lexer's and follow Settings | Editor | Color Scheme | Crucible.
 *
 * Token types stay standard LSP types so other editors color them sensibly;
 * what a declaration is (a service, a resource, an error) travels as a
 * modifier on the declaration and on every reference to it.
 */
class CrucibleSemanticTokens : LspSemanticTokensSupport() {
    /**
     * The platform asks for semantic tokens only in plain-text and TextMate
     * files, assuming any other language highlights itself. Crucible's lexer
     * cannot tell a service from an error; the server can.
     */
    override fun shouldAskServerForSemanticTokens(psiFile: PsiFile): Boolean = psiFile is CrucibleFile

    override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? =
        colorOf(tokenType, modifiers)

    companion object {
        fun colorOf(tokenType: String, modifiers: List<String>): TextAttributesKey? {
            KINDS.firstOrNull { (modifier, _) -> modifier in modifiers }?.let { return it.second }
            val declaration = "declaration" in modifiers
            return when (tokenType) {
                "keyword" -> CrucibleColors.KEYWORD
                "type", "struct", "enum", "class", "interface" -> when {
                    "defaultLibrary" in modifiers -> CrucibleColors.BUILTIN_TYPE
                    declaration -> CrucibleColors.TYPE_DECLARATION
                    else -> CrucibleColors.TYPE
                }
                "function" -> if (declaration) CrucibleColors.OPERATION_DECLARATION else CrucibleColors.OPERATION
                "property" -> CrucibleColors.MEMBER
                "enumMember" -> CrucibleColors.ENUM_VALUE
                "decorator" -> CrucibleColors.ANNOTATION
                "parameter" -> CrucibleColors.PARAMETER
                "string" -> CrucibleColors.STRING
                "number" -> CrucibleColors.NUMBER
                "comment" -> if ("documentation" in modifiers) CrucibleColors.DOC_COMMENT else CrucibleColors.LINE_COMMENT
                else -> null
            }
        }

        /** Kind modifiers, which win over the token type; a union keeps the structure colors. */
        private val KINDS = listOf(
            "error" to CrucibleColors.ERROR,
            "service" to CrucibleColors.SERVICE,
            "protocol" to CrucibleColors.PROTOCOL,
            "resource" to CrucibleColors.RESOURCE,
            "rule" to CrucibleColors.RULE,
        )
    }
}
