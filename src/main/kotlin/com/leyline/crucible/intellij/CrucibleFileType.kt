package com.leyline.crucible.intellij

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object CrucibleFileType : LanguageFileType(CrucibleLanguage) {
    override fun getName(): String = "Crucible"

    override fun getDescription(): String = CrucibleBundle.message("filetype.description")

    override fun getDefaultExtension(): String = "cru"

    override fun getIcon(): Icon = CrucibleIcons.FILE
}
