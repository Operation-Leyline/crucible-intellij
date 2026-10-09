package com.leyline.crucible.intellij.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Application-wide Crucible settings, stored in `crucible.xml`. */
@Service(Service.Level.APP)
@State(name = "CrucibleSettings", storages = [Storage("crucible.xml")])
class CrucibleSettings : SimplePersistentStateComponent<CrucibleSettings.State>(State()) {
    class State : BaseState() {
        /** Use [serverPath] instead of finding the server automatically. */
        var useCustomServer by property(false)
        var serverPath by string()
    }

    var useCustomServer: Boolean
        get() = state.useCustomServer
        set(value) {
            state.useCustomServer = value
        }

    var serverPath: String
        get() = state.serverPath.orEmpty()
        set(value) {
            state.serverPath = value.ifBlank { null }
        }

    companion object {
        fun getInstance(): CrucibleSettings = service()
    }
}
