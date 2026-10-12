package com.leyline.crucible.intellij

import com.google.gson.JsonObject
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.SystemInfo
import com.leyline.crucible.intellij.highlighting.CrucibleColors
import com.leyline.crucible.intellij.highlighting.CrucibleSemanticTokens
import com.leyline.crucible.intellij.lsp.CrucibleCodeLens
import com.leyline.crucible.intellij.lsp.CrucibleLspClientDescriptor
import com.leyline.crucible.intellij.lsp.CrucibleServerLocator
import com.leyline.crucible.intellij.lsp.ServerSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrucibleServerTest {
    private val exe = if (SystemInfo.isWindows) "crucible-lsp.exe" else "crucible-lsp"

    @Test
    fun `automatic lookup tries the environment, the bundle, PATH and Cargo in order`() {
        val plugin = Path.of("/plugins/crucible-intellij")
        val candidates = CrucibleServerLocator.candidates(
            useCustom = false,
            customPath = "/ignored",
            environment = mapOf("CRUCIBLE_LSP" to "/opt/crucible-lsp", "PATH" to "/a${File.pathSeparator}/b"),
            pluginPath = plugin,
        )
        assertEquals(
            listOf(ServerSource.ENVIRONMENT, ServerSource.BUNDLED, ServerSource.PATH, ServerSource.PATH, ServerSource.CARGO),
            candidates.map { it.second },
        )
        assertEquals(Path.of("/opt/crucible-lsp"), candidates[0].first)
        assertEquals(plugin.resolve("server").resolve(CrucibleServerLocator.platform()).resolve(exe), candidates[1].first)
        assertTrue(candidates.last().first.endsWith(Path.of(".cargo", "bin", exe)))
    }

    @Test
    fun `a custom server is the only candidate`() {
        assertEquals(
            listOf(Path.of("/custom/lsp") to ServerSource.CUSTOM),
            CrucibleServerLocator.candidates(true, "/custom/lsp", mapOf("PATH" to "/a"), null),
        )
        assertTrue(CrucibleServerLocator.candidates(true, " ", emptyMap(), null).isEmpty())
    }

    @Test
    fun `the bundled server is found and used first after the environment`() {
        val plugin = Files.createTempDirectory("crucible-plugin")
        val bundled = CrucibleServerLocator.bundled(plugin)
        Files.createDirectories(bundled.parent)
        Files.writeString(bundled, "")
        bundled.toFile().setExecutable(true)
        val found = CrucibleServerLocator.find(false, "", mapOf("PATH" to ""), plugin)
        assertEquals(ServerSource.BUNDLED, found?.source)
        assertNull(CrucibleServerLocator.find(true, plugin.resolve("missing").toString(), emptyMap(), plugin))
    }

    @Test
    fun `semantic tokens use the plugin's colors`() {
        assertEquals(CrucibleColors.TYPE_DECLARATION, CrucibleSemanticTokens.colorOf("struct", listOf("declaration")))
        assertEquals(CrucibleColors.TYPE, CrucibleSemanticTokens.colorOf("enum", emptyList()))
        assertEquals(CrucibleColors.BUILTIN_TYPE, CrucibleSemanticTokens.colorOf("type", listOf("defaultLibrary")))
        assertEquals(CrucibleColors.OPERATION_DECLARATION, CrucibleSemanticTokens.colorOf("function", listOf("declaration")))
        assertEquals(CrucibleColors.DOC_COMMENT, CrucibleSemanticTokens.colorOf("comment", listOf("documentation")))
        assertNull(CrucibleSemanticTokens.colorOf("macro", emptyList()))
    }

    @Test
    fun `every token type crucible-lsp emits has a color`() {
        // TOKEN_TYPES in crucible-lsp/src/analysis.rs, in legend order.
        val emitted = listOf(
            "keyword", "type", "struct", "enum", "class", "interface", "function",
            "property", "enumMember", "decorator", "parameter", "string", "number", "comment",
        )
        for (type in emitted) {
            assertNotNull(type, CrucibleSemanticTokens.colorOf(type, emptyList()))
        }
    }

    @Test
    fun `members are colored as fields where declared and where named`() {
        // The server sends a member as `property`: with `declaration` in a
        // structure, input or output body, without it in `paginated(items: items)`.
        assertEquals(CrucibleColors.MEMBER, CrucibleSemanticTokens.colorOf("property", listOf("declaration")))
        assertEquals(CrucibleColors.MEMBER, CrucibleSemanticTokens.colorOf("property", emptyList()))
        assertEquals(DefaultLanguageHighlighterColors.INSTANCE_FIELD, CrucibleColors.MEMBER.fallbackAttributeKey)
    }

    @Test
    fun `a declaration's kind colors it and every reference to it`() {
        assertEquals(CrucibleColors.SERVICE, CrucibleSemanticTokens.colorOf("interface", listOf("declaration", "service")))
        assertEquals(CrucibleColors.PROTOCOL, CrucibleSemanticTokens.colorOf("interface", listOf("protocol")))
        assertEquals(CrucibleColors.RESOURCE, CrucibleSemanticTokens.colorOf("class", listOf("resource")))
        assertEquals(CrucibleColors.ERROR, CrucibleSemanticTokens.colorOf("class", listOf("declaration", "error")))
        assertEquals(CrucibleColors.ERROR, CrucibleSemanticTokens.colorOf("class", listOf("error")))
        assertEquals(CrucibleColors.RULE, CrucibleSemanticTokens.colorOf("function", listOf("rule")))
        // Operations keep the function colors; a union is colored as a structure.
        assertEquals(CrucibleColors.OPERATION, CrucibleSemanticTokens.colorOf("function", emptyList()))
        assertEquals(CrucibleColors.TYPE_DECLARATION, CrucibleSemanticTokens.colorOf("struct", listOf("declaration", "union")))
    }

    @Test
    fun `the server command line carries the user's arguments and environment`() {
        val line = CrucibleLspClientDescriptor.commandLine(
            Path.of("/bin/crucible-lsp"),
            "--log \"a b\" -v",
            mapOf("RUST_BACKTRACE" to "1"),
            "/work",
        )
        assertEquals(listOf("--log", "a b", "-v"), line.parametersList.list)
        assertEquals("1", line.environment["RUST_BACKTRACE"])
        assertEquals(File("/work"), line.workDirectory)
        assertTrue(CrucibleLspClientDescriptor.commandLine(Path.of("x"), "", emptyMap(), null).parametersList.list.isEmpty())
    }

    @Test
    fun `a usages lens carries the declaration's position`() {
        val position = JsonObject().apply {
            addProperty("line", 3)
            addProperty("character", 9)
        }
        assertEquals(3 to 9, CrucibleCodeLens.positionOf(position))
        assertNull(CrucibleCodeLens.positionOf("file:///a.cru"))
        assertNull(CrucibleCodeLens.positionOf(JsonObject()))
    }
}
