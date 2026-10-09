package com.leyline.crucible.intellij.editor

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.leyline.crucible.intellij.CrucibleLanguage

/**
 * Settings | Editor | Code Style | Crucible: the indentation the language
 * server's formatter and the editor's Enter key both use.
 */
class CrucibleCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage(): Language = CrucibleLanguage

    override fun getCodeSample(settingsType: SettingsType): String = SAMPLE

    override fun getIndentOptionsEditor(): IndentOptionsEditor = IndentOptionsEditor(this)

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions,
    ) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 4
        indentOptions.USE_TAB_CHARACTER = false
    }

    override fun createConfigurable(
        baseSettings: CodeStyleSettings,
        modelSettings: CodeStyleSettings,
    ): CodeStyleConfigurable = object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
        override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
            object : TabbedLanguageCodeStylePanel(CrucibleLanguage, currentSettings, settings) {
                override fun initTabs(settings: CodeStyleSettings) {
                    addIndentOptionsTab(settings)
                }
            }
    }

    override fun getConfigurableDisplayName(): String = "Crucible"

    private companion object {
        val SAMPLE = """
            |structure City {
            |    name: CityName
            |    population?: integer { range 0..100000000 }
            |}
            |
            |operation GetCity reads City {
            |    http GET "/cities/{cityId}"
            |
            |    input {
            |        ${'$'}cityId
            |    }
            |}
            """.trimMargin()
    }
}
