package com.leyline.crucible.intellij

import com.intellij.openapi.util.SystemInfo
import com.leyline.crucible.intellij.cli.CrucibleCli
import com.leyline.crucible.intellij.cli.CruciblePackages
import com.leyline.crucible.intellij.cli.GenerateOption
import com.leyline.crucible.intellij.cli.GenerateTarget
import com.leyline.crucible.intellij.lsp.CrucibleServerLocator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrucibleCliTest {
    private val exe = if (SystemInfo.isWindows) "crucible.exe" else "crucible"

    @Test
    fun `the package is the nearest directory whose manifest has a model table`() {
        val root = Files.createTempDirectory("crucible-packages")
        Files.writeString(root.resolve("crucible.toml"), "schema_version = 1\n[rules.naming]\nseverity = \"error\"\n")
        val pkg = Files.createDirectories(root.resolve("api"))
        Files.writeString(pkg.resolve("crucible.toml"), "# The API.\n[model]\nnamespace = \"example.shop\"\n")
        val nested = Files.createDirectories(pkg.resolve("model/items"))
        val file = Files.writeString(nested.resolve("item.cru"), "structure Item {}\n")

        assertEquals(pkg, CruciblePackages.packageOf(file))
        assertEquals(pkg, CruciblePackages.packageOf(nested))
        assertEquals(pkg, CruciblePackages.packageOf(pkg.resolve("crucible.toml")))
        // A policy-only manifest is not a package.
        assertFalse(CruciblePackages.isPackage(root))
        assertNull(CruciblePackages.packageOf(root.resolve("crucible.toml")))
        assertEquals("example.shop", CruciblePackages.namespace(pkg))

        val dotted = Files.createDirectories(root.resolve("dotted"))
        Files.writeString(dotted.resolve("crucible.toml"), "model.namespace = \"a.b\"\n")
        assertTrue(CruciblePackages.isPackage(dotted))
        assertEquals("a.b", CruciblePackages.namespace(dotted))
        // Only the [model] table's namespace counts.
        val elsewhere = Files.createDirectories(root.resolve("elsewhere"))
        Files.writeString(elsewhere.resolve("crucible.toml"), "[model]\nsources = [\".\"]\n\n[rules.x]\nnamespace = \"wrong\"\n")
        assertNull(CruciblePackages.namespace(elsewhere))
        val spaced = Files.createDirectories(root.resolve("spaced"))
        Files.writeString(spaced.resolve("crucible.toml"), "[ model ] # the package\nnamespace = \"c.d\" # trailing\n")
        assertEquals("c.d", CruciblePackages.namespace(spaced))
        val naming = Files.createDirectories(root.resolve("naming"))
        Files.writeString(naming.resolve("crucible.toml"), "[model.naming]\ninput = \"{Operation}In\"\n")
        assertTrue(CruciblePackages.isPackage(naming))
    }

    @Test
    fun `the CLI is found in the settings, the environment, PATH and Cargo in order`() {
        val home = Path.of("/home/me")
        assertEquals(listOf(Path.of("/custom/crucible")), CrucibleCli.candidates("/custom/crucible", mapOf("PATH" to "/a"), home))
        val candidates = CrucibleCli.candidates(
            "",
            mapOf("CRUCIBLE_CLI" to "/opt/crucible", "PATH" to "/a${File.pathSeparator}/b", "CARGO_HOME" to "/cargo"),
            home,
        )
        assertEquals(
            listOf(
                Path.of("/opt/crucible"),
                Path.of("/a", exe),
                Path.of("/b", exe),
                Path.of("/cargo", "bin", exe),
                home.resolve(".cargo").resolve("bin").resolve(exe),
            ),
            candidates,
        )
    }

    @Test
    fun `check and generate command lines`() {
        val cli = Path.of("/bin/crucible")
        val pkg = Path.of("/work/api")
        assertEquals(listOf("check", pkg.toString()), CrucibleCli.check(cli, pkg).parametersList.list)

        val out = Path.of("/work/generated/ts-client")
        val ts = CrucibleCli.generate(cli, pkg, GenerateTarget.TYPESCRIPT, out, mapOf(GenerateOption.PACKAGE_NAME to " example-shop "))
        assertEquals(
            listOf("generate", "ts", pkg.toString(), "--out", out.toString(), "--package-name", "example-shop"),
            ts.parametersList.list,
        )
        assertEquals(File(pkg.toString()), ts.workDirectory)
        val rust = CrucibleCli.generate(cli, pkg, GenerateTarget.RUST, out, emptyMap())
        assertEquals(listOf("generate", "rust", pkg.toString(), "--out", out.toString(), "--client"), rust.parametersList.list)
        assertThrows(IllegalArgumentException::class.java) {
            CrucibleCli.generate(cli, pkg, GenerateTarget.GO, out, mapOf(GenerateOption.PACKAGE_NAME to "shop"))
        }
    }

    @Test
    fun `every target suggests each option it requires`() {
        for (target in GenerateTarget.entries) {
            val defaults = target.defaults("example.shop")
            assertEquals(target.options.toSet(), defaults.keys)
            assertTrue("$target: $defaults", defaults.values.all { it.isNotBlank() })
        }
        // Values the generators accept (checked against `crucible generate`).
        assertEquals("example_shop", GenerateTarget.RUBY.defaults("example.shop")[GenerateOption.PACKAGE_NAME])
        assertEquals("example::shop", GenerateTarget.CPP.defaults("example.shop")[GenerateOption.NAMESPACE])
        assertEquals("Example\\Shop", GenerateTarget.PHP.defaults("example.shop")[GenerateOption.NAMESPACE])
        assertEquals("example/shop", GenerateTarget.PHP.defaults("example.shop")[GenerateOption.PACKAGE_NAME])
        assertEquals("Example.Shop", GenerateTarget.CSHARP.defaults("example.shop")[GenerateOption.NAMESPACE])
    }

    @Test
    fun `quoted and malformed search path entries are skipped, not fatal`() {
        val home = Path.of("/home/me")
        // A NUL is invalid in a path on every platform, as a quote is on Windows.
        val path = listOf("\"/quoted dir\"", "bad\u0000dir", "/b").joinToString(File.pathSeparator)
        val candidates = CrucibleCli.candidates("", mapOf("PATH" to path), home)
        assertEquals(Path.of("/quoted dir", exe), candidates[0])
        assertEquals(Path.of("/b", exe), candidates[1])
        assertEquals(listOf(Path.of("/custom/crucible")), CrucibleCli.candidates("\"/custom/crucible\"", emptyMap(), home))
        val servers = CrucibleServerLocator.candidates(false, "", mapOf("PATH" to path), null).map { it.first }
        assertTrue(servers.any { it.startsWith(Path.of("/quoted dir")) })
        assertEquals(
            listOf(Path.of("/custom/lsp")),
            CrucibleServerLocator.candidates(true, " \"/custom/lsp\" ", emptyMap(), null).map { it.first },
        )
    }

    @Test
    fun `inline model tables and literal strings name the namespace`() {
        val root = Files.createTempDirectory("crucible-namespaces")
        fun manifest(name: String, text: String): Path =
            Files.createDirectories(root.resolve(name)).also { Files.writeString(it.resolve("crucible.toml"), text) }
        val inline = manifest("inline", "model = { namespace = \"i.n\", sources = [\".\"] }\n")
        assertTrue(CruciblePackages.isPackage(inline))
        assertEquals("i.n", CruciblePackages.namespace(inline))
        assertEquals("l.s", CruciblePackages.namespace(manifest("literal", "[model]\nnamespace = 'l.s'\n")))
        assertNull(CruciblePackages.namespace(manifest("none", "model = { sources = [\".\"] }\n")))
        assertEquals("my_api", CruciblePackages.fallbackNamespace(Path.of("/x/My-API")))
        assertEquals("model", CruciblePackages.fallbackNamespace(Path.of("/x/---")))
    }
}
