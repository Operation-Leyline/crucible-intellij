package com.leyline.crucible.intellij

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object CrucibleIcons {
    /** The `.cru` file icon; `crucible_dark.svg` is picked up for dark themes. */
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/crucible.svg", CrucibleIcons::class.java)
}
