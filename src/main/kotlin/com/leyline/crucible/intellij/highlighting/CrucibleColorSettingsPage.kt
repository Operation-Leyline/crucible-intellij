package com.leyline.crucible.intellij.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.leyline.crucible.intellij.CrucibleBundle
import com.leyline.crucible.intellij.CrucibleIcons
import javax.swing.Icon

/** Settings | Editor | Color Scheme | Crucible. */
class CrucibleColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = CrucibleIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = CrucibleSyntaxHighlighter()

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = CrucibleBundle.message("settings.display.name")

    companion object {
        private val DESCRIPTORS = arrayOf(
            descriptor("color.keyword", CrucibleColors.KEYWORD),
            descriptor("color.builtin", CrucibleColors.BUILTIN_TYPE),
            descriptor("color.type.declaration", CrucibleColors.TYPE_DECLARATION),
            descriptor("color.type.reference", CrucibleColors.TYPE),
            descriptor("color.operation.declaration", CrucibleColors.OPERATION_DECLARATION),
            descriptor("color.operation.reference", CrucibleColors.OPERATION),
            descriptor("color.service", CrucibleColors.SERVICE),
            descriptor("color.protocol", CrucibleColors.PROTOCOL),
            descriptor("color.resource", CrucibleColors.RESOURCE),
            descriptor("color.error", CrucibleColors.ERROR),
            descriptor("color.rule", CrucibleColors.RULE),
            descriptor("color.member", CrucibleColors.MEMBER),
            descriptor("color.enum.value", CrucibleColors.ENUM_VALUE),
            descriptor("color.annotation", CrucibleColors.ANNOTATION),
            descriptor("color.parameter", CrucibleColors.PARAMETER),
            descriptor("color.string", CrucibleColors.STRING),
            descriptor("color.number", CrucibleColors.NUMBER),
            descriptor("color.comment.line", CrucibleColors.LINE_COMMENT),
            descriptor("color.comment.doc", CrucibleColors.DOC_COMMENT),
            descriptor("color.braces", CrucibleColors.BRACES),
            descriptor("color.brackets", CrucibleColors.BRACKETS),
            descriptor("color.parentheses", CrucibleColors.PARENTHESES),
            descriptor("color.operator", CrucibleColors.OPERATOR),
            descriptor("color.punctuation", CrucibleColors.PUNCTUATION),
        )

        // Tags mark what only the language server can tell apart.
        internal val TAGS = mapOf(
            "decl" to CrucibleColors.TYPE_DECLARATION,
            "type" to CrucibleColors.TYPE,
            "op" to CrucibleColors.OPERATION_DECLARATION,
            "opref" to CrucibleColors.OPERATION,
            "member" to CrucibleColors.MEMBER,
            "value" to CrucibleColors.ENUM_VALUE,
            "param" to CrucibleColors.PARAMETER,
            "kw" to CrucibleColors.KEYWORD,
            "service" to CrucibleColors.SERVICE,
            "protocol" to CrucibleColors.PROTOCOL,
            "resource" to CrucibleColors.RESOURCE,
            "error" to CrucibleColors.ERROR,
            "rule" to CrucibleColors.RULE,
        )

        internal val DEMO = """
            |// Items in the catalog.
            |service <service>Catalog</service> {
            |    <kw>version</kw> "2026-10-01"
            |    <kw>protocol</kw> <protocol>CompactJson</protocol>
            |    <kw>resources</kw> [<resource>Item</resource>]
            |    <kw>errors</kw> [<error>ValidationError</error>]
            |}
            |
            |protocol <protocol>CompactJson</protocol> {}
            |
            |resource <resource>Item</resource> {
            |    <kw>id</kw> <member>itemId</member>: <type>ItemId</type>
            |}
            |
            |string <decl>ItemId</decl> { <kw>length</kw> 1..36 }
            |
            |/// Creates an item; a retry with the same token returns the first result.
            |operation <op>CreateItem</op> <kw>creates</kw> <resource>Item</resource> {
            |    <kw>http</kw> POST "/items" -> 201
            |    <kw>paginated</kw>(<param>items</param>: <member>items</member>)
            |
            |    <kw>input</kw> {
            |        <member>name</member>: string
            |        @token
            |        <member>clientToken</member>?: string
            |    }
            |    <kw>errors</kw> [<error>ItemExists</error>]
            |}
            |
            |error <error>ItemExists</error> {
            |    <kw>status</kw> 409
            |    <member>message</member>: string
            |}
            |
            |enum <decl>Color</decl> { <value>RED</value> = "red" }
            |
            |rule <rule>NamedOperations</rule> {
            |    <kw>every</kw> <kw>operation</kw> <kw>must</kw> <kw>has</kw> @summary
            |}
            """.trimMargin()

        private fun descriptor(key: String, attributes: TextAttributesKey) =
            AttributesDescriptor(CrucibleBundle.message(key), attributes)
    }
}
