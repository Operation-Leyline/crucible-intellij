package com.leyline.crucible.intellij.json

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.leyline.crucible.intellij.CrucibleBundle

/**
 * The JSON Schema for `crucible.toml`. The TOML plugin applies JSON schemas
 * to TOML files, so manifests get key completion, documentation and
 * validation as they are typed, before the language server's own checks.
 * Loaded only when the JSON plugin is present (`crucible-json.xml`).
 */
class CrucibleManifestSchemaFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(CrucibleManifestSchema)
}

object CrucibleManifestSchema : JsonSchemaFileProvider {
    const val RESOURCE = "/schemas/crucible.toml.json"

    override fun isAvailable(file: VirtualFile): Boolean = file.name == "crucible.toml"

    override fun getName(): String = CrucibleBundle.message("schema.manifest.name")

    override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(CrucibleManifestSchema::class.java, RESOURCE)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
