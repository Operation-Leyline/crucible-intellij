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
        /** Extra command-line arguments, split as a shell would split them. */
        var serverArguments by string()
        /** Variables added to the server's environment, for example `RUST_BACKTRACE`. */
        var serverEnvironment by map<String, String>()
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

    var serverArguments: String
        get() = state.serverArguments.orEmpty()
        set(value) {
            state.serverArguments = value.ifBlank { null }
        }

    var serverEnvironment: Map<String, String>
        get() = state.serverEnvironment.toMap()
        set(value) {
            // A new map, not one changed in place, so the state sees the modification.
            state.serverEnvironment = value.toMutableMap()
        }

    companion object {
        fun getInstance(): CrucibleSettings = service()
    }
}
