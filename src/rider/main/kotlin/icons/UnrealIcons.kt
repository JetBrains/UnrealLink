package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class Status {
        companion object {
            val Connected = IconLoader.getIcon("/Icons/Status/unrealStatusOn.svg")
            val Disconnected = IconLoader.getIcon("/Icons/Status/unrealStatus.svg")
            val Pause = IconLoader.getIcon("/Icons/Status/unrealStatusPause.svg")
            val Play = IconLoader.getIcon("/Icons/Status/unrealStatusPlay.svg")
            val Stop = IconLoader.getIcon("/Icons/Status/unrealStatusBreakpoint.svg")
        }
    }
}