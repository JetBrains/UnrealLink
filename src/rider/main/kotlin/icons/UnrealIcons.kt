package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class Status {
        companion object {
            val Connected = IconLoader.getIcon("/Icons/Status/unrealStatusOn.svg", UnrealIcons::class.java)
            val Disconnected = IconLoader.getIcon("/Icons/Status/unrealStatus.svg", UnrealIcons::class.java)
            val Pause = IconLoader.getIcon("/Icons/Status/unrealStatusPause.svg", UnrealIcons::class.java)
            val Play = IconLoader.getIcon("/Icons/Status/unrealStatusPlay.svg", UnrealIcons::class.java)
            val Stop = IconLoader.getIcon("/Icons/Status/unrealStatusBreakpoint.svg", UnrealIcons::class.java)
            val FrameSkip = IconLoader.getIcon("/Icons/Status/unrealFrameSkip.svg", UnrealIcons::class.java)
        }
    }
}