import java.io.File
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The IDE supplies the Kotlin standard library; never call newer API.
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
        allWarningsAsErrors = true
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformLocalPath = providers.gradleProperty("platformLocalPath")

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        if (platformLocalPath.isPresent) {
            local(platformLocalPath)
        } else {
            create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    // No GUI forms or Java @NotNull to instrument; Kotlin checks nullability itself.
    instrumentCode = false

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // The description is the README section between the plugin description markers.
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("README.md must hold the plugin description between $start and $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            if (platformLocalPath.isPresent) {
                local(file(platformLocalPath.get()))
            } else {
                create(IntelliJPlatformType.IntellijIdeaUltimate, providers.gradleProperty("platformVersion"))
                create(IntelliJPlatformType.RustRover, providers.gradleProperty("platformVersion"))
            }
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// ---- the bundled language server ------------------------------------------
//
// crucible-lsp ships inside the plugin, under server/<platform>/, so it works
// with no install. -PcrucibleLspDir=<dir> takes the release archives
// (crucible-lsp-<rust target>.zip, as Crucible releases attach them);
// -PcrucibleLsp=<file> takes one local build for this machine's platform.
// Without either, the plugin falls back to a crucible-lsp on PATH.

val rustTargets = mapOf(
    "x86_64-unknown-linux-gnu" to "linux-x64",
    "aarch64-unknown-linux-gnu" to "linux-arm64",
    "x86_64-apple-darwin" to "macos-x64",
    "aarch64-apple-darwin" to "macos-arm64",
    "x86_64-pc-windows-msvc" to "windows-x64",
)

fun hostPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    return when {
        os.startsWith("windows") -> "windows-x64"
        os.startsWith("mac") -> if (arm) "macos-arm64" else "macos-x64"
        else -> if (arm) "linux-arm64" else "linux-x64"
    }
}

val serverArchives = providers.gradleProperty("crucibleLspDir")
val serverBinary = providers.gradleProperty("crucibleLsp")

val prepareServer = tasks.register<Sync>("prepareServer") {
    description = "Collects the crucible-lsp binaries the plugin bundles."
    into(layout.buildDirectory.dir("server"))
    if (serverArchives.isPresent) {
        val archives = file(serverArchives.get()).listFiles().orEmpty().filter { it.name.endsWith(".zip") }
        for (archive in archives) {
            val target = archive.name.removePrefix("crucible-lsp-").removeSuffix(".zip")
            val platform = rustTargets[target] ?: throw GradleException("unknown server target in ${archive.name}")
            from(zipTree(archive)) { into(platform) }
        }
    }
    if (serverBinary.isPresent) {
        from(serverBinary) {
            into(hostPlatform())
            rename { if (it.endsWith(".exe")) "crucible-lsp.exe" else "crucible-lsp" }
        }
    }
    filePermissions { unix("rwxr-xr-x") }
}

// Every sandbox (the distribution, runIde, tests and smoke) gets the server.
tasks.withType<PrepareSandboxTask>().configureEach {
    from(prepareServer) {
        into(intellijPlatform.projectName.map { "$it/server" })
    }
}

// ---- headless smoke ---------------------------------------------------------
//
// `./gradlew smoke` opens a real project in a headless IDE with the plugin and
// a test-only application starter (src/smoke), and checks highlighting, the
// bundled server, completion and the IDE's own error highlighting. The starter
// is never part of the distributed plugin.

val smoke: SourceSet = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val smokeJar = tasks.register<Jar>("smokeJar") {
    description = "Packages the test-only smoke starter as its own plugin."
    archiveFileName = "crucible-intellij-smoke.jar"
    destinationDirectory = layout.buildDirectory.dir("smoke")
    // The platform plugin adds the main descriptor to every resource set; the
    // starter's own descriptor is kept apart and added here instead.
    from(smoke.output) { exclude("META-INF/plugin.xml") }
    from("src/smoke/descriptor") { into("META-INF") }
}

val smokeIdePath = providers.gradleProperty("smokeIdePath")

// Opening a project writes to it, so the smoke opens a copy.
val smokeProject = tasks.register<Sync>("smokeProject") {
    from("src/smoke/project")
    into(layout.buildDirectory.dir("smoke/project"))
}

intellijPlatformTesting {
    runIde {
        register("smokeIde") {
            if (smokeIdePath.isPresent) {
                localPath = file(smokeIdePath.get())
            } else {
                type = IntelliJPlatformType.IntellijIdeaUltimate
                version = providers.gradleProperty("platformVersion")
            }
            // The starter is its own plugin, copied into this run's sandbox only.
            prepareSandboxTask {
                from(smokeJar) { into("crucible-smoke/lib") }
                // The sandbox disables JetBrains Ultimate, and the platform LSP client
                // (with this plugin) goes with it; the smoke needs the real IDE.
                val disabled = sandboxConfigDirectory.file("disabled_plugins.txt")
                doLast { disabled.get().asFile.delete() }
            }
            task {
                dependsOn(smokeJar, smokeProject)
                val project = layout.buildDirectory.dir("smoke/project").get().asFile.absolutePath
                val bundled = serverArchives.isPresent || serverBinary.isPresent
                args = listOf("crucibleSmoke", project)
                val sandboxPlugins = sandboxPluginsDirectory
                jvmArgumentProviders += CommandLineArgumentProvider {
                    // The generated plugin list joins entries with the wrong separator on
                    // Windows, so the starter plugin would not load; name both explicitly.
                    val plugins = listOf("crucible-intellij", "crucible-smoke")
                        .joinToString(File.pathSeparator) { sandboxPlugins.get().dir(it).asFile.absolutePath }
                    listOf(
                        "-Djava.awt.headless=true",
                        "-Didea.trust.all.projects=true",
                        "-Dcrucible.smoke.expectBundled=$bundled",
                        "-Dplugin.path=$plugins",
                    )
                }
            }
        }
    }
}

tasks.register("smoke") {
    group = "verification"
    description = "Runs the plugin and its bundled language server in a headless IDE."
    dependsOn("smokeIde")
}

tasks.wrapper {
    gradleVersion = "9.8.0"
}
