package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class ConnectionStatus {
        companion object {
            // TODO: add proper icon for settings
            val UnrealEngineLogo = IconLoader.getIcon("/Icons/toolWindows/toolWindowUnrealLog.svg", UnrealIcons::class.java)
            val UnrealEngineConnected = IconLoader.getIcon("/Icons/run/unrealEngineConnected.svg", UnrealIcons::class.java)
            val UnrealEngineDisconnected = IconLoader.getIcon("/Icons/run/unrealEngineDisconnected.svg", UnrealIcons::class.java)
        }
    }

    class PIEControl {
        companion object {
            val Pause = IconLoader.getIcon("/Icons/run/unrealStatusPause.svg", UnrealIcons::class.java)
            val Play = IconLoader.getIcon("/Icons/run/unrealStatusPlay.svg", UnrealIcons::class.java)
            val Stop = IconLoader.getIcon("/Icons/run/unrealStatusStop.svg", UnrealIcons::class.java)
            val FrameSkip = IconLoader.getIcon("/Icons/run/unrealFrameSkip.svg", UnrealIcons::class.java)

        }
    }

    class ToolWindow {
        companion object {
            val UnrealToolWindow = IconLoader.getIcon("/Icons/toolWindows/toolWindowUnrealLog.svg", UnrealIcons::class.java)
        }
    }
}