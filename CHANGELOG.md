# Changelog

## [Unreleased]

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
- `crucible.toml` completion, documentation and validation from a bundled
  JSON Schema, when the JSON and TOML plugins are installed.
- Server arguments and environment variables in the settings.
