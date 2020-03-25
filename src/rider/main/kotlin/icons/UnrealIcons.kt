package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class Status {
        companion object {
            val Connected =IconLoader.getIcon("Icons/Status/UnrealConnected.svg")
            val Disconnected =IconLoader.getIcon("Icons/Status/UnrealDisconnected.svg")
        }
    }
}