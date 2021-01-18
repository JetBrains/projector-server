# projector-plugin
This subproject is an IntelliJ plugin for sharing the IDE window using the Projector server.

Please note that it's an experimental technology.

If you want simultaneous collaborative editing, please try [Code With Me](https://www.jetbrains.com/help/idea/code-with-me.html) solution.
Projector doesn't support that.

## Building from sources

This will give you a zip file with IntelliJ plugin:

```shell script
./gradlew :projector-plugin:buildPlugin
```

This command creates a zip file in the `projector-plugin/build/distributions` dir.

## Downloading

Alternatively, you can download the zip file from [releases](https://github.com/JetBrains/projector-server/releases/). Please search for the
latest release starting with `agent-...` and find the plugin in Assets there.

## Usage

Install the plugin (the zip file) into IntelliJ IDEA via `Gear Icon | Install plugin from disk...` menu item in Plugins settings. New menu
item `Projector` will appear next to the `Help` in the title bar.
