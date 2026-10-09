package com.leyline.crucible.intellij.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Defaults
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/**
 * Every color the plugin uses, each falling back to the matching platform
 * color so any scheme looks right. The lexer and the language server's
 * semantic tokens share these keys.
 */
object CrucibleColors {
    @JvmField val KEYWORD = key("CRUCIBLE_KEYWORD", Defaults.KEYWORD)
    @JvmField val BUILTIN_TYPE = key("CRUCIBLE_BUILTIN_TYPE", Defaults.KEYWORD)
    @JvmField val TYPE = key("CRUCIBLE_TYPE", Defaults.CLASS_REFERENCE)
    @JvmField val TYPE_DECLARATION = key("CRUCIBLE_TYPE_DECLARATION", Defaults.CLASS_NAME)
    @JvmField val OPERATION = key("CRUCIBLE_OPERATION", Defaults.FUNCTION_CALL)
    @JvmField val OPERATION_DECLARATION = key("CRUCIBLE_OPERATION_DECLARATION", Defaults.FUNCTION_DECLARATION)
    @JvmField val MEMBER = key("CRUCIBLE_MEMBER", Defaults.INSTANCE_FIELD)
    @JvmField val ENUM_VALUE = key("CRUCIBLE_ENUM_VALUE", Defaults.CONSTANT)
    @JvmField val ANNOTATION = key("CRUCIBLE_ANNOTATION", Defaults.METADATA)
    @JvmField val PARAMETER = key("CRUCIBLE_PARAMETER", Defaults.PARAMETER)
    @JvmField val STRING = key("CRUCIBLE_STRING", Defaults.STRING)
    @JvmField val NUMBER = key("CRUCIBLE_NUMBER", Defaults.NUMBER)
    @JvmField val LINE_COMMENT = key("CRUCIBLE_LINE_COMMENT", Defaults.LINE_COMMENT)
    @JvmField val DOC_COMMENT = key("CRUCIBLE_DOC_COMMENT", Defaults.DOC_COMMENT)
    @JvmField val BRACES = key("CRUCIBLE_BRACES", Defaults.BRACES)
    @JvmField val BRACKETS = key("CRUCIBLE_BRACKETS", Defaults.BRACKETS)
    @JvmField val PARENTHESES = key("CRUCIBLE_PARENTHESES", Defaults.PARENTHESES)
    @JvmField val OPERATOR = key("CRUCIBLE_OPERATOR", Defaults.OPERATION_SIGN)
    @JvmField val PUNCTUATION = key("CRUCIBLE_PUNCTUATION", Defaults.COMMA)

    private fun key(name: String, fallback: TextAttributesKey) = createTextAttributesKey(name, fallback)
}
