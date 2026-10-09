package com.leyline.crucible.intellij

import com.intellij.openapi.util.SystemInfo
import com.leyline.crucible.intellij.highlighting.CrucibleColors
import com.leyline.crucible.intellij.highlighting.CrucibleSemanticTokens
import com.leyline.crucible.intellij.lsp.CrucibleServerLocator
import com.leyline.crucible.intellij.lsp.ServerSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
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
}
