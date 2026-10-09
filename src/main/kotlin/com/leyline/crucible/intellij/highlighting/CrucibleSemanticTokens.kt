package com.leyline.crucible.intellij.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport

/**
 * Paints the server's semantic tokens (`TOKEN_TYPES` in
 * crucible-lsp/src/analysis.rs) with the plugin's own colors, so they match
 * the lexer's and follow Settings | Editor | Color Scheme | Crucible.
 */
class CrucibleSemanticTokens : LspSemanticTokensSupport() {
    override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? =
        colorOf(tokenType, modifiers)

    companion object {
        fun colorOf(tokenType: String, modifiers: List<String>): TextAttributesKey? {
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
    }
}
