package com.leyline.crucible.intellij.lsp

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import com.leyline.crucible.intellij.settings.CrucibleSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Where a server came from, for the settings page and diagnostics. */
enum class ServerSource { CUSTOM, ENVIRONMENT, BUNDLED, PATH, CARGO }

data class ResolvedServer(val path: Path, val source: ServerSource)

/**
 * Finds `crucible-lsp`. A custom path from the settings wins. Otherwise, in
 * order: `CRUCIBLE_LSP`, the server bundled with this plugin for this
 * platform, `crucible-lsp` on `PATH`, and Cargo's bin directory (an IDE
 * started from the desktop often lacks the shell's `PATH`).
 */
object CrucibleServerLocator {
    fun find(): ResolvedServer? = find(
        useCustom = CrucibleSettings.getInstance().useCustomServer,
        customPath = CrucibleSettings.getInstance().serverPath,
        environment = System.getenv(),
        // The plugin's own class loader knows where the plugin is installed.
        pluginPath = (CrucibleServerLocator::class.java.classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.pluginPath,
    )

    fun find(useCustom: Boolean, customPath: String, environment: Map<String, String>, pluginPath: Path?): ResolvedServer? =
        candidates(useCustom, customPath, environment, pluginPath).firstOrNull { (path, _) -> usable(path) }
            ?.let { (path, source) -> ResolvedServer(path, source) }

    fun candidates(
        useCustom: Boolean,
        customPath: String,
        environment: Map<String, String>,
        pluginPath: Path?,
    ): List<Pair<Path, ServerSource>> {
        if (useCustom) {
            return listOfNotNull(pathOf(customPath)?.let { it to ServerSource.CUSTOM })
        }
        val out = mutableListOf<Pair<Path, ServerSource>>()
        environment["CRUCIBLE_LSP"]?.let(::pathOf)?.let { out += it to ServerSource.ENVIRONMENT }
        pluginPath?.let { out += bundled(it) to ServerSource.BUNDLED }
        searchPath(environment).forEach { dir -> pathOf(dir, EXECUTABLE)?.let { out += it to ServerSource.PATH } }
        environment["CARGO_HOME"]?.let { pathOf(it, "bin", EXECUTABLE) }?.let { out += it to ServerSource.CARGO }
        pathOf(System.getProperty("user.home"), ".cargo", "bin", EXECUTABLE)?.let { out += it to ServerSource.CARGO }
        return out
    }

    /** The bundled server for this machine, as the build lays it out under `server/<platform>/`. */
    fun bundled(pluginPath: Path): Path = pluginPath.resolve("server").resolve(platform()).resolve(EXECUTABLE)

    fun platform(): String {
        val arm = CpuArch.isArm64()
        return when {
            SystemInfo.isWindows -> "windows-x64"
            SystemInfo.isMac -> if (arm) "macos-arm64" else "macos-x64"
            else -> if (arm) "linux-arm64" else "linux-x64"
        }
    }

    private val EXECUTABLE = if (SystemInfo.isWindows) "crucible-lsp.exe" else "crucible-lsp"

    private fun usable(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        if (!Files.isExecutable(path)) {
            // Plugin installation can drop the executable bit on macOS and Linux.
            runCatching { path.toFile().setExecutable(true) }
        }
        return Files.isExecutable(path)
    }
}

/**
 * A path from user or environment text, or null when it names none. Windows
 * allows quoted `PATH` entries and "Copy as path" quotes too; `Path.of`
 * rejects the quote character, and one bad entry must not stop the search.
 */
internal fun pathOf(first: String, vararg more: String): Path? {
    val trimmed = first.trim().removeSurrounding("\"")
    if (trimmed.isEmpty()) return null
    return runCatching { Path.of(trimmed, *more) }.getOrNull()
}

/** The directories of `PATH`, as Windows or Unix spells the variable. */
internal fun searchPath(environment: Map<String, String>): List<String> =
    (environment["PATH"] ?: environment["Path"])?.split(File.pathSeparator)?.filter { it.isNotBlank() }.orEmpty()
