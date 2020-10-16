# projector-plugin
This subproject is an IntelliJ plugin for sharing the IDE window using the Projector server.

Please note that it's an experimental technology.

If you want simultaneous collaborative editing, please try [Code With Me](https://www.jetbrains.com/help/idea/code-with-me.html) solution. Projector doesn't support that.

## Building
This will give you a zip file with IntelliJ plugin:

```shell script
./gradlew :projector-plugin:buildPlugin
```

This command creates a zip file in the `projector-plugin/build/distributions` dir.

## Usage
Install the plugin into IntelliJ IDEA via `Install plugin from disk...` menu item in Plugins settings. New menu item `Projector` will appear next to the `Help` in the title bar.
