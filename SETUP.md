<h1>Setting up development environment</h1>

<p>After cloning the repository, you need to perform some actions to set up the environment.</p>

<h2>[TBD] Setting up UnrealLink frontend</h2>

<h2>Setting up UnrealLink backend</h2>
<ul>
    <li>    
        Run the console under the folder where you have cloned the repository.
        <p><strong>NOTE:</strong> Make sure that you have Java installed on your machine and you have environmental variable JAVA_HOME set to the value of the path to your JDK folder. Otherwise, it won't work.</p>
    </li>
    <li>
        Use the following command <code>gradlew buildResharperHost</code> to install all the requirements for proper work
    </li>
</ul>
<p>Here you go! Now you can open UnrealLink.sln and work on backend code.</p>

<h2>Setting up RiderLink, Unreal Editor plugin</h2>
<p>Code for the Unreal Editor part is stored inside UnrealLink repository, but in order to work with cpp code, it should be a part of Unreal Engine project.
As a workaround, we provide an option to create a junction from RiderLink source folder to Unreal Engine game project.
That way, when you make changes to RiderLink plugin inside Unreal Engine project, they will be picked up automatically in git.</p>
<ul>
    <li>Run the console under the folder where you have cloned the repository</li>
    <li>Use the following command <code>gradlew symlinkPluginToUnrealProject</code>
        <ul>
            <li>It will fail the first time with the message "Add path to a valid UnrealEngine project folder to: {UnrealLinkRoot}\UnrealEngineProjectPath.txt"</li>
            <li>and will create "UnrealEngineProjectPath.txt" for you.</li>
        </ul>
    </li>
    <li>Add path to the root folder of your game project into "UnrealEngineProjectPath.txt" eg "D:/PROJECTS/UE/basic_4_26"</li>
    <li>Run <code>gradlew symlinkPluginToUnrealProject</code> again.</li>
    <li>Refresh project files for your Game project</li>
</ul>
<p>RiderLink plugin will be available under <code>{GameProjectRoot}/Plugins/Development/RiderLink</code></p>



