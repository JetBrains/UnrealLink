package icons

import com.intellij.openapi.util.IconLoader

class UnrealIcons {
    class ConnectionStatus {
        companion object {
            // TODO: add proper icon for settings
            val UnrealEngineLogo = IconLoader.getIcon("/icons/toolWindows/toolWindowUnrealLog.svg", UnrealIcons::class.java)
            val UnrealEngineConnected = IconLoader.getIcon("/icons/run/unrealEngineConnected.svg", UnrealIcons::class.java)
            val UnrealEngineDisconnected = IconLoader.getIcon("/icons/run/unrealEngineDisconnected.svg", UnrealIcons::class.java)
        }
    }

    class PIEControl {
        companion object {
            val Pause = IconLoader.getIcon("/icons/run/unrealStatusPause.svg", UnrealIcons::class.java)
            val Play = IconLoader.getIcon("/icons/run/unrealStatusPlay.svg", UnrealIcons::class.java)
            val Stop = IconLoader.getIcon("/icons/run/unrealStatusStop.svg", UnrealIcons::class.java)
            val FrameSkip = IconLoader.getIcon("/icons/run/unrealFrameSkip.svg", UnrealIcons::class.java)

        }
    }

    class ToolWindow {
        companion object {
            val UnrealToolWindow = IconLoader.getIcon("/icons/toolWindows/toolWindowUnrealLog.svg", UnrealIcons::class.java)
        }
    }
}