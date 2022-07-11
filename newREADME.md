# UnrealLink for Unreal Engine

The UnrealLink plugin enables advanced integration between JetBrains
[Rider](https://www.jetbrains.com/lp/rider-unreal/) and Epic Games'
[Unreal Editor](https://www.unrealengine.com/en-US/).

Rider is a fast and powerful IDE for Unreal Engine and C++ development.
It provides rich code navigation, inspections, refactorings, understands
Blueprints and the Unreal Engine reflection mechanism, and supports
HLSL. The *Unreal Engine edition* of Rider is currently available as a
free Early Preview for Windows only. [Join the Early
Preview](https://www.jetbrains.com/rider/unreal/).

The plugin brings Blueprints information to the editor, adds settings to
manage game launch, and provides a more convenient version of the Unreal
Editor log.

-   [Plugin structure](#plugin-structure)
-   [Setting up development environment](#setup-environment)
-   [Installation](#installation)
-   [Features](#features)
-   [What could possibly go wrong?](#what-could-possibly-go-wrong)

## Plugin structure

There are two plugins under the hood, the **UnrealLink** plugin for
Rider and the **RiderLink** plugin for Unreal Editor, packed together.

## Setting up development environment

For the instruction on how to setup development environment and
contribute to the project, please, refer to [Setting up the
environment](SETUP.md) page

## Installation

**UnrealLink** is bundled with Rider. Starting with Rider for Unreal
Engine 2020.2.1, it's also distributed via the JetBrains plugin
[marketplace](https://plugins.jetbrains.com/plugin/14989-unreal-link).

**RiderLink** is installed by Rider itself, there is no need to install
it manually. The first time you open an Unreal Engine project in Rider,
you\'ll see a notification that the RiderLink plugin is missing and an
invitation to install it. If you skip this popup message, you can
install the plugin later by going to the Rider settings on the
*Languages and Frameworks \| Unreal Engine* page.

Both the popup message and the settings page offer two installation
options:

-   *Engine*: Select this option to install the plugin in the engine and
    use it for all game projects based on the current engine version.
    The plugin will appear in the `Engine/Plugins/Developer` folder.
-   *Game*: Select this option to install the plugin in the game project
    and use it for the current project only. The plugin will appear in
    the `Game/Plugins/Developer` folder.

## Features

### Interact with blueprints

Blueprint files are written in binary form and are usually edited
visually. However, they contain a whole lot of useful information for
the developers of the C++ part of the game.

Rider reads Blueprints and allows you to see the bigger picture behind
your code:

-   There may be derived blueprint classes, which you can see by
    invoking *Find Usages* on a C++ class or when you\'re browsing your
    C++ code in the editor.
-   You can see the values of overridden properties.

UnrealLink extends this functionality and introduces the ability to
navigate to the Blueprint inside the Unreal Editor from your C++ code.

![Interact with
blueprints](https://plugins.jetbrains.com/files/14989/screenshot_23450.png)

### Manage the game

The plugin allows you to manage your game right inside the IDE: select
the running mode, run a server for your multiplayer game, specify the
number of players, and more.

![Manage the
game](https://plugins.jetbrains.com/files/14989/screenshot_23451.png)

### Browse the Unreal Editor log

UnrealLink offers you an enhanced version of the Unreal Editor log
output panel with colored text for easy reading, as well as verbosity
and event category filters. You can also click on any highlighted link
to navigate to the related source code line.

![Browse the Unreal Editor
log](https://plugins.jetbrains.com/files/14989/screenshot_23452.png)
[Learn more about Rider for Unreal Engine
\>\>](https://www.jetbrains.com/help/rider/Working_with_Unreal_Engine.html)

## What could possibly go wrong?

The plugin and Rider for Unreal Engine itself are in active development
now, so there could be some issues. Please share your feedback and
report any bugs you encounter:

-   Submit plugin-specific issues to the [GitHub Issues
    page](https://github.com/JetBrains/UnrealLink/issues).
-   Rider-specific issues should be directed to the [Rider
    tracker](https://youtrack.jetbrains.com/issues/RIDER).
-   Send a message with any questions and feature suggestions to our
    support engineers and the Rider for Unreal Engine developers at
    <rider-cpp-support@jetbrains.com>. We really love hearing from you!

A few typical issues, and what to do in such cases:

##### Failed to build RiderLink plugin

\`\`\` Failed to build RiderLink plugin Check build logs for more info
Help \> Diagnostic Tools \> Show Log in Explorer And contact dev team
for help at GitHub Issues page \`\`\`

There are several reasons you might get this message:

-   There's a problem with your current Game or Unreal Engine code.
    Please make sure that you can build them correctly.
-   You have an instance of Unreal Editor with the RiderLink plugin
    running. Please close Unreal Editor and try installing RiderLink
    again.
-   Finally, if Unreal Editor is closed and your project builds fine,
    and you have an old version of RiderLink installed, please move the
    old version of RiderLink to a temp folder manually and try
    reinstalling RiderLink.

##### Failed to backup old plugin

\`\`\` Failed to backup old plugin Close all running instances of Unreal
Editor and try again Path to old plugin: \`\`\`

You tried to install a new version of RiderLink while you have a running
instance of Unreal Editor with the plugin installed. Please close Unreal
Editor and try again to install the plugin.

##### Failed to refresh project files

This warning message means that installation was successful, but
updating the project files in Rider failed. Everything should work fine,
except the plugin will not appear in the `/Plugins/Developer` folder in
the Explorer view.

If you have any issues with the plugin that you can't resolve, please
contact the developers via [GitHub
Issues](https://github.com/JetBrains/UnrealLink/issues).
