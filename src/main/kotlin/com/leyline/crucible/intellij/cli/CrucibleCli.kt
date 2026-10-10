package com.leyline.crucible.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.leyline.crucible.intellij.settings.CrucibleSettings
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `crucible` CLI, which checks a package and generates clients from it.
 * Unlike the language server it is not bundled: it is found like a toolchain,
 * from the settings, `CRUCIBLE_CLI`, `PATH` or Cargo's bin directory.
 */
object CrucibleCli {
    private val EXECUTABLE = if (SystemInfo.isWindows) "crucible.exe" else "crucible"

    fun find(): Path? = find(CrucibleSettings.getInstance().cliPath, System.getenv(), Path.of(System.getProperty("user.home")))

    fun find(customPath: String, environment: Map<String, String>, home: Path): Path? =
        candidates(customPath, environment, home).firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }

    fun candidates(customPath: String, environment: Map<String, String>, home: Path): List<Path> {
        if (customPath.isNotBlank()) return listOf(Path.of(customPath))
        // `add`, not `+=`: a Path is itself an Iterable of paths.
        val out = mutableListOf<Path>()
        environment["CRUCIBLE_CLI"]?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it)) }
        (environment["PATH"] ?: environment["Path"])?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach {
            out.add(Path.of(it, EXECUTABLE))
        }
        environment["CARGO_HOME"]?.takeIf { it.isNotBlank() }?.let { out.add(Path.of(it, "bin", EXECUTABLE)) }
        out.add(home.resolve(".cargo").resolve("bin").resolve(EXECUTABLE))
        return out
    }

    /** `crucible check <package>`: every problem the build would report. */
    fun check(cli: Path, packageDir: Path): GeneralCommandLine =
        command(cli, packageDir, listOf("check", packageDir.toString()))

    /** `crucible generate <target> <package> --out <dir>` with the target's required options. */
    fun generate(cli: Path, packageDir: Path, target: GenerateTarget, out: Path, options: Map<GenerateOption, String>): GeneralCommandLine {
        val arguments = mutableListOf("generate", target.id, packageDir.toString(), "--out", out.toString())
        arguments += target.extraArguments
        for (option in target.options) {
            val value = options[option]?.trim().orEmpty()
            require(value.isNotEmpty()) { "${option.flag} is required for ${target.id}" }
            arguments += listOf(option.flag, value)
        }
        return command(cli, packageDir, arguments)
    }

    private fun command(cli: Path, packageDir: Path, arguments: List<String>): GeneralCommandLine =
        GeneralCommandLine(cli.toString())
            .withParameters(arguments)
            .withWorkDirectory(packageDir.toFile())
            .withCharset(StandardCharsets.UTF_8)
}

/**
 * Crucible language packages: a directory whose `crucible.toml` has a `[model]`
 * table, as `crucible_lang::is_package` decides. A policy-only `crucible.toml`
 * is not one.
 */
object CruciblePackages {
    private val MODEL = Regex("""^\s*(\[\s*model\s*[.\]]|model\s*\.)""", RegexOption.MULTILINE)
    private val TABLE = Regex("""^\s*\[\[?\s*([^\]]*?)\s*]""")
    private val STRING_KEY = Regex("""^\s*([A-Za-z0-9_.\-\s]+?)\s*=\s*"([^"]*)"""")

    /** The package `path` (a file or directory) belongs to: the nearest such directory at or above it. */
    fun packageOf(path: Path): Path? {
        var dir: Path? = if (Files.isDirectory(path)) path else path.parent
        while (dir != null) {
            if (isPackage(dir)) return dir
            dir = dir.parent
        }
        return null
    }

    fun isPackage(dir: Path): Boolean = manifest(dir)?.let { MODEL.containsMatchIn(it) } == true

    /**
     * The package namespace, used to suggest package and module names: the
     * `namespace` key of the `[model]` table, or a top-level `model.namespace`.
     * A `namespace` in another table is not it.
     */
    fun namespace(dir: Path): String? {
        val text = manifest(dir) ?: return null
        var table = ""
        for (line in text.lines()) {
            TABLE.find(line)?.let {
                table = it.groupValues[1].filterNot(Char::isWhitespace)
                continue
            }
            val (key, value) = STRING_KEY.find(line)?.destructured ?: continue
            val path = listOf(table, key.filterNot(Char::isWhitespace)).filter { it.isNotEmpty() }.joinToString(".")
            if (path == "model.namespace") return value.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun manifest(dir: Path): String? = runCatching {
        dir.resolve("crucible.toml").takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }
    }.getOrNull()
}

/** An option a client target cannot do without. */
enum class GenerateOption(val flag: String, val label: String) {
    PACKAGE_NAME("--package-name", "Package name:"),
    MODULE_NAME("--module-name", "Module name:"),
    MODULE_PATH("--module-path", "Module path:"),
    GROUP_ID("--group-id", "Group ID:"),
    ARTIFACT_ID("--artifact-id", "Artifact ID:"),
    NAMESPACE("--namespace", "Namespace:"),
    PACKAGE_ID("--package-id", "Package ID:"),
    PREFIX("--prefix", "Symbol prefix:"),
}

/**
 * The client targets of `crucible generate` and the options each requires,
 * with defaults derived from the package namespace (`example.shop`) that the
 * generators accept. Server, document and infrastructure outputs are left to
 * the build, where they belong.
 */
enum class GenerateTarget(
    val id: String,
    val label: String,
    val options: List<GenerateOption>,
    val extraArguments: List<String> = emptyList(),
) {
    TYPESCRIPT("ts", "TypeScript", listOf(GenerateOption.PACKAGE_NAME)),
    PYTHON("python", "Python", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.MODULE_NAME)),
    GO("go", "Go", listOf(GenerateOption.MODULE_PATH, GenerateOption.PACKAGE_NAME)),
    JAVA("java", "Java", listOf(GenerateOption.GROUP_ID, GenerateOption.ARTIFACT_ID, GenerateOption.PACKAGE_NAME)),
    KOTLIN("kotlin", "Kotlin", listOf(GenerateOption.GROUP_ID, GenerateOption.ARTIFACT_ID, GenerateOption.PACKAGE_NAME)),
    SWIFT("swift", "Swift", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.MODULE_NAME)),
    CSHARP("csharp", "C#", listOf(GenerateOption.NAMESPACE, GenerateOption.PACKAGE_ID)),
    DART("dart", "Dart", listOf(GenerateOption.PACKAGE_NAME)),
    PHP("php", "PHP", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.NAMESPACE)),
    RUBY("ruby", "Ruby", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.MODULE_NAME)),
    CPP("cpp", "C++", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.NAMESPACE)),
    C("c", "C", listOf(GenerateOption.PACKAGE_NAME, GenerateOption.PREFIX)),
    R("r", "R", listOf(GenerateOption.PACKAGE_NAME)),
    RUST("rust", "Rust", emptyList(), listOf("--client")),
    ;

    override fun toString(): String = label

    /** Suggested values for [options], from a namespace such as `example.shop`. */
    fun defaults(namespace: String): Map<GenerateOption, String> {
        val parts = namespace.split('.').filter { it.isNotBlank() }.ifEmpty { listOf("api") }
        val last = parts.last()
        val snake = parts.joinToString("_") { it.lowercase() }
        val kebab = parts.joinToString("-") { it.lowercase() }
        val pascal = parts.joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
        val packageName = when (this) {
            TYPESCRIPT, PYTHON -> kebab
            GO -> last.lowercase()
            JAVA, KOTLIN -> namespace
            SWIFT -> pascal
            PHP -> "${parts.first().lowercase()}/${parts.drop(1).joinToString("-").ifEmpty { "client" }.lowercase()}"
            R -> parts.joinToString("") { it.lowercase() }
            else -> snake
        }
        return options.associateWith { option ->
            when (option) {
                GenerateOption.PACKAGE_NAME -> packageName
                GenerateOption.MODULE_NAME -> if (this == PYTHON) snake else pascal
                GenerateOption.MODULE_PATH -> "example.com/$kebab"
                GenerateOption.GROUP_ID -> parts.dropLast(1).joinToString(".").ifEmpty { last }
                GenerateOption.ARTIFACT_ID -> "$last-client"
                GenerateOption.NAMESPACE -> when (this) {
                    PHP -> parts.joinToString("\\") { part -> part.replaceFirstChar { it.uppercase() } }
                    CPP -> parts.joinToString("::") { it.lowercase() }
                    else -> parts.joinToString(".") { part -> part.replaceFirstChar { it.uppercase() } }
                }
                GenerateOption.PACKAGE_ID -> parts.joinToString(".") { part -> part.replaceFirstChar { it.uppercase() } }
                GenerateOption.PREFIX -> last.lowercase()
            }
        }
    }
}
