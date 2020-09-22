# projector-plugin
This subproject is an IntelliJ plugin for sharing the IDE window using the Projector server.

Please note that it's an experimental technology.

If you want simultaneous collaborative editing, please try [Code With Me](https://www.jetbrains.com/help/idea/code-with-me.html) solution. Projector doesn't support that.

## Building
This will give you a zip file with Idea plugin:

```shell script
./gradlew :projector-plugin:buildPlugin
```

This command creates a zip file in the `projector-plugin/build/distributions` dir.

## Usage
Install the plugin into IDEA via `Install plugin from disk...` menu item in Plugins settings. New menu item `Projector` will appear next to the `Help` in the title bar.

## Emergency brake 
Plugin sets itself as javaagent by modifying the `vmoptions` file. In case of serious problems with javaagent the whole IDEA may not start. To fix it you have to manually remove a line containing `-javaagent` and `projector-plugin` from your `vmoptions` file.  
  
You can find this file as described here: <https://www.jetbrains.com/help/idea/tuning-the-ide.html#config-directory>. Examples:
* Linux: `~/.<product><version>/config` (example `~/.IntelliJIdea2019.3/config`).
* macOS: `~/Library/Preferences/<product><version>` (example `~/Library/Preferences/IntelliJIdea2019.3`).
* Windows: `%HOMEPATH%\.<product><version>\config` (example `C:\Users\JohnS\.IntelliJIdea2019.3\config`).
