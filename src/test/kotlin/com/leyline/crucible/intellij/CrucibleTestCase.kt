package com.leyline.crucible.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.leyline.crucible.intellij.settings.CrucibleSettings

/**
 * A platform test with no language server. Opening a `.cru` file would
 * otherwise start any `crucible-lsp` installed on the machine, in the test's
 * temporary project, which then cannot be deleted while the server runs.
 * The setting is never restored: the platform may start a client after
 * tearDown, and the test application's settings are not saved anyway.
 */
abstract class CrucibleTestCase : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrucibleSettings.getInstance().useCustomServer = true
        CrucibleSettings.getInstance().serverPath = ""
    }
}
