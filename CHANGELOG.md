# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0). Note that this project does not follow semantic versioning but uses version numbers based on JetBrains [Rider](https://www.jetbrains.com/rider/) releases.

This plugin contains a plugin for the Unreal Editor (RiderLink) that is used to communicate with Rider. Changes marked with a "Rider:" prefix are specific to Rider, while changes for the Unreal Editor plugin are marked with a "Unreal Editor:" prefix. No prefix means that the change is common to both Rider and ReSharper.

The plugin is always bundled with Rider.

## [Unreleased]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Exception on generitng project files during installation of RiderLink plugin
- Rider not being able to connect to Unreal Editor 

### Known Issues
## [2021.1.1]
### Added
- Support MacOS and Linux
- Installing RiderLink plugin logs show in Build panel

### Changed

### Deprecated

### Removed

### Fixed

### Known Issues
## [2020.3.116]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fix https://github.com/JetBrains/UnrealLink/issues/62
### Known Issues
## [2020.3.113]
### Added

### Changed
- Build RiderLink plugin from source on user PC instead of bundling dll files.
  - Reason: frequent changes of toolchains and breaking of ABI compatibility in MSVC toolchain render this practice impossible.

### Deprecated
- Drop support for UE 4.21 and older
  - Reason: RD framework doesn't use PCH files and doesn't comply with UE rules for include files. It's impossible to state that module doesn't use PCH files in UE 4.21 and older.

### Removed

### Fixed

### Known Issues
## [2020.3.104]
### Added
- Proper README for the https://github.com/JetBrains/UnrealLink

### Changed

### Deprecated

### Removed

### Fixed
- Fix memory leak in log panel on exit of Rider
- Fix deprecated code 
### Known Issues
## [2020.3.85]
### Added
- Supported Rider 2020.3

### Changed

### Deprecated

### Removed

### Fixed

### Known Issues
## [2020.2.83]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- More fixes to https://youtrack.jetbrains.com/issue/RIDER-50354

### Known Issues
## [2020.2.79]
### Added
- More info and notifications when RiderLink can't be built
- Unable auto-update plugin from JetBrains Marketplace
### Changed

### Deprecated

### Removed
- Removed verbose log messages from RD framework in stdout

### Fixed
- Fix https://youtrack.jetbrains.com/issue/RIDER-50354
- Fix corrupted hyperlinks after toggling timestamps
### Known Issues
## [2020.2.69]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fix https://github.com/JetBrains/UnrealLink/issues/46

### Known Issues
## [2020.2.67]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fix showing popup "update project file" after installing RiderLink
  - After pressing "update project file", it'd update {GameProject}.uproject file, adding reference to RiderLink. This is not necessary for RiderLink to work properly, but it'll break project for people that will sink to {GameProject}.uproject, but who doesn't have RiderLink installed

### Known Issues
## [2020.2.66]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fix https://github.com/JetBrains/UnrealLink/issues/40
- Fix https://youtrack.jetbrains.com/issue/RIDER-47839

### Known Issues
## [2020.2.62]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fixed: https://github.com/JetBrains/UnrealLink/issues/19

### Known Issues
## [2020.2.61]
### Added
- Unreal log filters
  - Verbosity
  - Category
  - Show/hide timestamps
### Changed
- Generated files for RD protocol will have ".Generated.h" suffix

### Deprecated

### Removed
- Packing plugin for ReSharper
  - Not supported yet

### Fixed
- Fixed: building RiderLink with Unity build disabled
- Fixed: Wording in "Install RiderLink" popup
- Fixed: Wording in UnrealEngine settings
  - No more auto-install plugin, only auto-update plugin

### Known Issues

## [2020.2.36]
### Added

### Changed
- rd-cpp updated to 201.1.85
### Deprecated

### Removed

### Fixed
- Fixed stackoverflow crash on exit of Unreal Editor
### Known Issues

## [2020.2.34]
### Added

### Changed
- move plugin installation in backend thread - no more freeze of UI when installing RiderLink

### Deprecated

### Removed

### Fixed
- fix loading RiderLink plugin on cooking
- fix crash on closing UnrealEditor in RiderLoggingExtension
- fixed number of problems discovering Unreal Editor installation, should fix a lot of false failing installations of RiderLink
- fix wording in RiderLink settings panel ( auto update and install -> auto update)
- fix UnrealBuildTool removing EnableByDefault: true from RiderLink.uplugin
  - No more modification of {GameName}.uproject file to enable RiderLink

### Known Issues

## [2020.1.10]
### Added
- Add option to install plugin to either Engine or Game
  - Installing to Engine from EGS requires building plugin
  - Old versions of plugins will be backed up
  - New version will be copied to %USER% folder and built
  - Result binaries copyied to Engine or Game
  - Sln will be refreshed
  - In case of errors, all the results will be reverted
### Changed
- Moved thirdparty dependencies of RD to submodules, fixed build.kts for it
### Deprecated

### Removed

### Fixed
- [Extremely long log entry causes RiderLink to lose connection to Unreal Engine](https://github.com/JetBrains/UnrealLink/issues/17)
- Fix looking for Unreal Engine root when Game is placed in the same folder as Engine
- Fix RiderLink for non-Unity builds
### Known Issues

## [2020.1.8]
### Added
- Add progress bar when installing "RiderLink" plugin
- Hide notification suggesting installing RiderLink after selecting "Install plugin"
- [Add option to Compile Game Project before starting PIE](https://github.com/JetBrains/UnrealLink/issues/9)
### Changed
- Installation to Engine instead of game Project
  - [Add option to install RiderLink to Engine instead of Game Project](https://github.com/JetBrains/UnrealLink/issues/7)
  - During installation process, RiderLink will be built and put into correct folder
  - No more gimmicks with "build plugin in Game project, then copy-paste it in Engine project"
- Refactored "RiderLink"
  - Separated "RiderLink" into separate module with different loading steps
  - e.g. For "RiderLink', start as soon as possible, for "RiderLinkGameControlExtension" start after Unreal Editor is fully loaded, etc
  - Fixed all warnings (TEXT macro redefined, obsolete API, etc)
### Deprecated

### Removed

### Fixed
- Lower verbosity of RD protocol logger
  - Less noise in debug console
- [Build on non-unity projects](https://github.com/JetBrains/UnrealLink/issues/10)
- [Exception during installation of RiderLink](https://github.com/JetBrains/UnrealLink/issues/5)
- [Multiple popups suggesting installing RiderLink](https://youtrack.jetbrains.com/issue/RIDER-43928)
- Bunch of issues:
  - https://youtrack.jetbrains.com/issue/RIDER-44056
  - https://youtrack.jetbrains.com/issue/RIDER-44027
  - https://youtrack.jetbrains.com/issue/RIDER-43929

### Known Issues

## [2020.1.7]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- [Incorrect time in log](https://github.com/JetBrains/UnrealLink/issues/8)
- [Infinite process: RiderLink Discovering Asset Data](https://youtrack.jetbrains.com/issue/RIDER-43353)
- Spawn of multiple tabs when sending huge log message from Unreal Editor to Rider
### Known Issues

## [2020.1.6]
### Added
- FileWatcher initialization moved to separate thread, speed up startup time for plugin on RIder side
- New icons for Play actions and connection status
### Changed
- Cleaned up severity of internal logs
  - Were polluting package logs
### Deprecated

### Removed

### Fixed
- Connecting to Unreal Editor when plugin is installed in Engine
### Known Issues

## [2020.1.5]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- [Cant close Unreal log window](https://github.com/JetBrains/UnrealLink/issues/2)
  - Disabled spawning multiple tabs for logs. Only one log tab for Unreal Editor log will be available at all times
- [Rider installs plugins to Programs](https://github.com/JetBrains/UnrealLink/issues/6)
- [Exception: The process cannot access the file because it is being used by another process](https://github.com/JetBrains/UnrealLink/issues/5)
- [Crash in RiderLink when packaging project](https://github.com/JetBrains/UnrealLink/issues/4)
### Known Issues

## [2020.1.4]
### Added
- Option to enable auto-update Unreal Editor (RiderLink) plugin
- Notification after plugin installation
### Changed

### Deprecated

### Removed

### Fixed
- Not finding uproject files on loading solution
- [UnrealLink plugin generates wrong sln for Rocket builds of UE](https://github.com/JetBrains/UnrealLink/issues/1)
### Known Issues

## [2020.1.2]
### Added

### Changed

### Deprecated

### Removed

### Fixed
- Fixed: when adding RiderLink plugin to project, Rider would regenerate sln and It would add Programs folder to solution tree for Rocket builds (UE from EGS)
- Fixed: building plugin on v4.20+
- Fixed: Use user temp folder instead of the project folder for intermediate file operations to avoid asking for elevated options

### Known Issues

## [2020.1.1]
### Added
- Open Blueprints from Rider
- Show logs of Unreal Editor inside Rider with hyperlinks to Blueprints inside Rider
- Toolbar buttons to control playback of the game inside Unreal Editor
  - Play, Pause, Stop, Skip frame;
  - Setup multiplayer: number of players, dedicated server, etc.
- Notification if connection to Unreal Editor is established or not (bottom right corner of status bar);
- Auto-install plugins in project;
  - Setting to enable auto-install (`true` by default).
  - If auto-install is disabled - notification will show up with quick-fix action.

### Changed

### Deprecated

### Removed

### Fixed

### Known Issues
- Service for opening assets in Unreal Editor has changed starting with UE 4.24 - opening same BP multiple times will open multiple tabs instead of re-using already opened one;
- Icon for connection status looks weird, waiting for new icons from designers
- Connecting to multiple instances of Unreal Editor is not working correctly
  - Toolbar controls will be affecting only the last connected editor
