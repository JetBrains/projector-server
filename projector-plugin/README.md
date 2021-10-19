# projector-plugin

This subproject is an IntelliJ plugin for sharing the IDE window using the Projector server.

If you want simultaneous collaborative editing, please try [Code With Me](https://www.jetbrains.com/help/idea/code-with-me.html) solution.
Projector doesn't support that.

## Downloading from Plugins Marketplace

The plugin is published here: <https://plugins.jetbrains.com/plugin/16015-projector>. So you can find it in IDE and install it (manual IDE
restart is needed after installation).

## Usage

New widget `Projector` will appear in the toolbar. 
If toolbar widget can't be installed menu `Projector` will appear next to the `Help` menu.

### Messages
To attract user attention to something plugin can display a message. Message can be disposed by pressing "Got It" button.

The following messages are used:
  - Greeting message - displayed for each new plugin installation (but not oftener once per day) 
  to attract user attention to the widget. Plugin updates do not trigger the message.
    
  - Mac local connection warning message - alerts the user about [keyboard input issue](https://youtrack.jetbrains.com/issue/PRJ-321) when connected locally on Mac once per local connection.
  



## Building from sources

This will give you a zip file with IntelliJ plugin:

```shell script
./gradlew :projector-plugin:buildPlugin  # Java 11 is required
```

This command creates a zip file in the `projector-plugin/build/distributions` dir.

## Downloading zip file from releases

Alternatively, you can download the zip file from [releases](https://github.com/JetBrains/projector-server/releases/). Please search for the
latest release starting with `agent-...` and find the plugin in Assets there.

## Installing zip file

Install the plugin (the zip file) into IntelliJ-based IDE via `Gear Icon | Install plugin from disk...` menu item in Plugins settings.
