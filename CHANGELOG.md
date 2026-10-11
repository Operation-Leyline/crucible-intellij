# Changelog

## [Unreleased]

- Bundles `crucible-lsp` from Crucible 0.5.0, the first release that ships it.
- Semantic highlighting, highlighting of other uses and rename now actually
  go through `crucible-lsp` in `.cru` files. The platform asks a language
  server for these only in plain-text and TextMate files unless a plugin opts
  in, so `.cru` files had lexer colors only and the colors by kind never
  showed.
- `.cru` file type with lexical highlighting, a color settings page, line
  comments, brace and quote pairing, smart indentation and TODO support.
- `crucible-lsp` bundled and started through the platform LSP client: the
  compiler's diagnostics, completion, semantic highlighting, go to declaration,
  find usages, rename, highlighting of other uses, hover, the structure view,
  folding, Reformat Code and quick fixes. `crucible.toml` manifests are checked.
- Settings | Languages & Frameworks | Crucible to choose the server executable.
- Settings | Editor | Code Style | Crucible for indentation.
- New | Crucible File for model files, services and package manifests.
- Services, protocols, resources, errors and rules get colors of their own,
  in declarations and in every reference, from the server's semantic token
  modifiers. Errors are red by default in light and dark schemes.
- Ctrl+hover underlines a name that Go to Declaration can reach; Go to
  Declaration asks `crucible-lsp` directly.
- Reformat Code on a `.cru` file always runs the compiler's formatter.
- Usage counts above declarations open the usages popup when clicked; inlay
  hints, parameter info, go to type declaration, document links and
  reformatting a selection come from the server.
- Enter in a `///` doc comment continues it on the next line.
- Live templates for structures, unions, enums, errors, resources, operations
  and services, and Surround With for input, output and structure bodies.
- `crucible.toml` completion, hover documentation and validation come from
  `crucible-lsp`, which also reports the package's API policy in `.cru`
  files as you type, with a rename to the configured case as a quick fix.
- Server arguments and environment variables in the settings.
- Tools | Crucible | Check Crucible Package and Generate Crucible Client run
  the `crucible` CLI, found from the settings, `CRUCIBLE_CLI`, `PATH` or
  Cargo's bin directory.
