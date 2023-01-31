[![official JetBrains project](https://jb.gg/badges/official-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# UnrealLink for Unreal Engine

<p>The UnrealLink plugin enables advanced integration between JetBrains <a href="https://www.jetbrains.com/lp/rider-unreal/">Rider</a> and Epic Games’ <a href="https://www.unrealengine.com/en-US/">Unreal Editor</a>.</p>

<p>Rider is a fast and powerful IDE for Unreal Engine and C++ development. It provides rich code navigation, inspections, refactorings, understands Blueprints and the Unreal Engine reflection mechanism, and supports HLSL. The <em>Unreal Engine edition </em>of Rider is currently available as a free Early Preview for Windows only. <a href="https://www.jetbrains.com/rider/unreal/">Join the Early Preview</a>.</p>

<p>The plugin brings Blueprints information to the editor, adds settings to manage game launch, and provides a more convenient version of the Unreal Editor log.</p>

<ul>
    <li>
        <a href="#plugin-structure">Plugin structure</a>
    </li>
    <li>
        <a href="#setup-environment">Setting up development environment</a>
    </li>
    <li>
        <a href="#installation">Installation</a>
    </li>
    <li>
        <a href="#features">Features</a>
    </li>
    <li>
        <a href="#what-could-possibly-go-wrong">What could possibly go wrong?</a>
    </li>
</ul>
 
<h2 id="plugin-structure">Plugin structure</h2>
<p>There are two plugins under the hood, the <strong>UnrealLink</strong> plugin for Rider and the <strong>RiderLink</strong> plugin for Unreal Editor, packed together.</p>

<h2 id="setup-environment">Setting up development environment</h2>
<p>For the instruction on how to setup development environment and contribute to the project, please, refer to <a href="SETUP.md">Setting up the environment</a> page</p>

<h2 id="installation">Installation</h2>
<p><strong>UnrealLink</strong> is bundled with Rider. Starting with Rider for Unreal Engine 2020.2.1, it’s also distributed via the JetBrains plugin <a href="https://plugins.jetbrains.com/plugin/14989-unreal-link">marketplace</a>.</p>

<p><strong>RiderLink</strong> is installed by Rider itself, there is no need to install it manually. The first time you open an Unreal Engine project in Rider, you'll see a notification that the RiderLink plugin is missing and an invitation to install it. If you skip this popup message, you can install the plugin later by going to the Rider settings on the <em>Languages and Frameworks | Unreal Engine</em> page.</p>

<p>Both the popup message and the settings page offer two installation options:</p>
<ul>
    <li>
        <em>Engine</em>: Select this option to install the plugin in the engine and use it for all game projects based on the current engine version. The plugin will appear in the <code>Engine/Plugins/Developer</code> folder.
    </li>
    <li>
        <em>Game</em>: Select this option to install the plugin in the game project and use it for the current project only. The plugin will appear in the <code>Game/Plugins/Developer</code> folder.
    </li>
</ul>

<h2 id="features">Features</h2>
<h3 id="interact_with_blueprints">Interact with blueprints</h3>
<p>Blueprint files are written in binary form and are usually edited visually. However, they contain a whole lot of useful information for the developers of the C++ part of the game.</p>

<p>Rider reads Blueprints and allows you to see the bigger picture behind your code:</p>
<ul>
    <li>
        There may be derived blueprint classes, which you can see by invoking <em>Find Usages</em> on a C++ class or when you're browsing your C++ code in the editor.
    </li>
    <li>
        You can see the values of overridden properties.
    </li>
</ul>

<p>UnrealLink extends this functionality and introduces the ability to navigate to the Blueprint inside the Unreal Editor from your C++ code.</p>
<img alt="Interact with blueprints" width="800" src="https://plugins.jetbrains.com/files/14989/screenshot_23450.png"/>

<h3 id="manage_the_game">Manage the game</h3>
<p>The plugin allows you to manage your game right inside the IDE: select the running mode, run a server for your multiplayer game, specify the number of players, and more.</p>
<img alt="Manage the game" width="800" src="https://plugins.jetbrains.com/files/14989/screenshot_23451.png"/>

<h3 id="browse_the_unreal_editor_log">Browse the Unreal Editor log</h3>
<p>UnrealLink offers you an enhanced version of the Unreal Editor log output panel with colored text for easy reading, as well as verbosity and event category filters. You can also click on any highlighted link to navigate to the related source code line.</p>
<img alt="Browse the Unreal Editor log" width="800" src="https://plugins.jetbrains.com/files/14989/screenshot_23452.png"/>

<a href="https://www.jetbrains.com/help/rider/Working_with_Unreal_Engine.html">Learn more about Rider for Unreal Engine >></a>

<h2 id="what-could-possibly-go-wrong">What could possibly go wrong?</h2>
<p>The plugin and Rider for Unreal Engine itself are in active development now, so there could be some issues. Please share your feedback and report any bugs you encounter:</p>
<ul>
    <li>
        Submit plugin-specific issues to the <a href="https://github.com/JetBrains/UnrealLink/issues">GitHub Issues page</a>.
    </li>
    <li>
        Rider-specific issues should be directed to the <a href="https://youtrack.jetbrains.com/issues/RIDER">Rider tracker</a>.
    </li>
    <li>
        Send a message with any questions and feature suggestions to our support engineers and the Rider for Unreal Engine developers at <a href="mailto:rider-cpp-support@jetbrains.com">rider-cpp-support@jetbrains.com</a>. We really love hearing from you!
    </li>
</ul>

<p>A few typical issues, and what to do in such cases:</p>

<h5 id=failed_build>Failed to build RiderLink plugin</h5>

```
Failed to build RiderLink plugin
Check build logs for more info
Help > Diagnostic Tools > Show Log in Explorer
And contact dev team for help at GitHub Issues page
```

<p>There are several reasons you might get this message:</p>
<ul>
    <li>
        There’s a problem with your current Game or Unreal Engine code. Please make sure that you can build them correctly.
    </li>
    <li>
        You have an instance of Unreal Editor with the RiderLink plugin running. Please close Unreal Editor and try installing RiderLink again.
    </li>
    <li>
        Finally, if Unreal Editor is closed and your project builds fine, and you have an old version of RiderLink installed, please move the old version of RiderLink to a temp folder manually and try reinstalling RiderLink.
    </li>
</ul>

<h5 id=failed_backup>Failed to backup old plugin</h5>

```
Failed to backup old plugin
Close all running instances of Unreal Editor and try again
Path to old plugin:
```

<p>You tried to install a new version of RiderLink while you have a running instance of Unreal Editor with the plugin installed. Please close Unreal Editor and try again to install the plugin.</p>

<h5 id=failed_refresh>Failed to refresh project files</h5>

<p>This warning message means that installation was successful, but updating the project files in Rider failed. Everything should work fine, except the plugin will not appear in the <code>/Plugins/Developer</code> folder in the Explorer view.</p>

<p>If you have any issues with the plugin that you can’t resolve, please contact the developers via <a href="https://github.com/JetBrains/UnrealLink/issues">GitHub Issues</a>.</p>
