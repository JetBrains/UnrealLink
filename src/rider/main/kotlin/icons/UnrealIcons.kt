package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class Status {
        companion object {
            val UnrealEngineLogo = IconLoader.getIcon("/Icons/Status/UnrealEngineLogo.svg", UnrealIcons::class.java)
            val UnrealEngineConnected = IconLoader.getIcon("/Icons/Status/UnrealEngineConnected.svg", UnrealIcons::class.java)
            val UnrealEngineDisconnected = IconLoader.getIcon("/Icons/Status/UnrealEngineDisconnected.svg", UnrealIcons::class.java)
            val Pause = IconLoader.getIcon("/Icons/Status/unrealStatusPause.svg", UnrealIcons::class.java)
            val Play = IconLoader.getIcon("/Icons/Status/unrealStatusPlay.svg", UnrealIcons::class.java)
            val Stop = IconLoader.getIcon("/Icons/Status/unrealStatusBreakpoint.svg", UnrealIcons::class.java)
            val FrameSkip = IconLoader.getIcon("/Icons/Status/unrealFrameSkip.svg", UnrealIcons::class.java)
        }
    }
}