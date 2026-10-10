# Crucible for JetBrains IDEs

Editor support for [Crucible](https://github.com/Operation-Leyline/crucible)'s
modelling language in RustRover, IntelliJ IDEA and the other JetBrains IDEs
from 2026.2.

<!-- Plugin description -->
Editor support for Crucible's modelling language (`.cru` files) and package
manifests (`crucible.toml`).

- The compiler's errors as you type, across the whole package, with quick fixes
- Completion that knows where you are: declarations, clauses, types, errors, annotations and constraints
- Semantic highlighting that tells services, protocols, resources, operations, errors and rules apart, with colors under Settings | Editor | Color Scheme | Crucible
- Go to declaration (Ctrl+hover underlines what it can reach), go to type declaration, find usages, usage counts above declarations, rename and highlighting of other uses
- Hover documentation, parameter info, inlay hints, the structure view, breadcrumbs and folding
- Reformat Code with the compiler's own formatter, for a file or a selection
- Smart indentation, `///` doc comment continuation, brace and quote pairing, line comments and TODOs
- Live templates for declarations, and Surround With for input, output and structure bodies
- `crucible.toml` completion and validation from its JSON Schema
- New | Crucible File for model files, services and manifests

The plugin bundles `crucible-lsp`, the language server built from Crucible's
own compiler, so nothing else needs installing.
<!-- Plugin description end -->

## Install

Download `crucible-intellij-<version>.zip` from the
[releases](https://github.com/Operation-Leyline/crucible-intellij/releases),
then use Settings | Plugins | ⚙ | Install Plugin from Disk and restart the IDE.

The release ZIP bundles `crucible-lsp` for Linux, macOS (Intel and Apple
Silicon) and Windows. To use another build, for example one from a Crucible
checkout, choose it under Settings | Languages & Frameworks | Crucible, or set
`CRUCIBLE_LSP`. The same page takes extra server arguments and environment
variables. The language server widget in the status bar shows the server's
state and restarts it.

To get updates automatically, add the release's `updatePlugins.xml` URL as a
custom plugin repository under Settings | Plugins | ⚙ | Manage Plugin
Repositories. The IDE needs to reach that URL without signing in, so host the
file and ZIPs somewhere it can, such as an internal web server.

## How it works

Crucible's compiler is written in Rust. Rather than reimplement its grammar,
the plugin starts `crucible-lsp`, which runs the compiler itself, through the
IDE's built-in LSP client. Errors, completion, navigation, rename, folding,
formatting and semantic highlighting all come from the server, so the editor
and `cargo build` never disagree. The plugin adds what must be instant or is
IDE-specific: lexical highlighting before the server answers, indentation,
brace and quote pairing, comments, TODOs, file and live templates, the
`crucible.toml` schema and settings. Go to Declaration asks the server
directly, so Ctrl+hover can underline a name before it is clicked: the IDE's
LSP client resolves names only while the action itself runs.

A `.cru` file belongs to the nearest directory above it whose `crucible.toml`
has a `[model]` table. The server compiles that whole package on every
change, using unsaved editor text.

## Develop

Needs JDK 21. The build downloads the target IDE, or uses an installed one
when `platformLocalPath` is set, for example in `~/.gradle/gradle.properties`.

```sh
./gradlew check            # unit and platform tests
./gradlew verifyPlugin     # JetBrains Plugin Verifier against IntelliJ IDEA and RustRover
./gradlew buildPlugin -PcrucibleLsp=/path/to/crucible-lsp   # ZIP in build/distributions
./gradlew runIde -PcrucibleLsp=/path/to/crucible-lsp        # a sandbox IDE with the plugin
./gradlew smoke -PcrucibleLsp=/path/to/crucible-lsp         # headless IDE end to end
```

`-PcrucibleLsp` bundles one local server build for this machine; build it
in a Crucible checkout with `cargo build --release -p crucible-lsp`.
`-PcrucibleLspDir=<dir>` bundles the per-platform archives a Crucible release
attaches (`crucible-lsp-<target>.zip`), as the release workflow does.

`smoke` opens `src/smoke/project` in a headless IntelliJ IDEA with the plugin
and a test-only starter. It checks the editor integration, then starts the
bundled server and checks hover, semantic tokens and completion through the
IDE's own LSP client, and the compiler's error through the IDE's highlighting
passes. Set `smokeIdePath` to use an installed IntelliJ IDEA; RustRover needs
a license to run headless.

## Release

Set `pluginVersion` in `gradle.properties`, move the `Unreleased` notes in
`CHANGELOG.md` under it, and push a `v<version>` tag. The release workflow
downloads `crucible-lsp` for every platform from the Crucible release named by
`crucibleVersion`, builds and verifies the plugin, and publishes the ZIP and
`updatePlugins.xml`.
