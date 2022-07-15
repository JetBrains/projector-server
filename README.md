# projector-server
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Tests status badge](https://github.com/JetBrains/projector-server/workflows/Tests/badge.svg)](https://github.com/JetBrains/projector-server/actions)

Server-side library for running Swing applications remotely.

[Documentation](https://jetbrains.github.io/projector-client/mkdocs/latest/) | [Issue tracker](https://youtrack.jetbrains.com/issues/PRJ)

## The state of Projector

The development of JetBrains Projector as its own standalone product has been suspended. That said, Projector remains an important part of [JetBrains Gateway](https://www.jetbrains.com/remote-development/gateway/), which is the primary remote development tool for JetBrains IDEs. We will focus our efforts on improving and developing Projector in this limited scenario.

Our goal is to provide a rich, full-featured remote development experience with a look and feel that is equal to or better than what you get when working with IDEs locally. The only way to get everything you’re used to having when working locally (low latency, low network traffic, user-defined and OS-specific shortcuts, themes, settings migrations, ssh-agent/port forwarding, and other things) is by installing a dedicated client-side application. The standalone version of Projector is not capable of meeting these goals.

As a result, we no longer recommend using the standalone version of JetBrains Projector or merely making tweaks to incorporate it into your existing IDE servers. We won’t provide user support or quick-fixes for issues that arise when these setups are used. If you have the option to switch from Projector to Gateway, we strongly recommend you do so.

[Learn more about JetBrains Gateway](https://www.jetbrains.com/remote-development/gateway/)

## Building
The following command creates a zip file with the whole runtime classpath:

```shell script
./gradlew :projector-server:distZip
```

You can find the file here: `/<project-root>/projector-server/build/distibution/projector-server-<server-version>.zip`.

By default, a proper revision of `projector-client:projector-common` at GitHub will be used as a dependency. If you want to **use
local** `projector-client`, please specify a special local property `useLocalProjectorClient=true` as a line in `local.properties` file (
create this file if you don't have one). You can find an example in [local.properties.example](local.properties.example) file. After
specifying this property and reloading Gradle build script, local `projector-client` from `../projector-client` will be used as the
dependency.

to use the local projector-client, the folders must be located as follows:
```shell
.
├── projector-client
├── projector-server
```

## How to run my application using this?
There are two ways.

### Not modifying your application code
This is the recommended way. You can use it if you don't have any preference. You don't need to rebuild your app at all here.

In the `projector-server` project, there is a `ProjectorLauncher` main class. It sets headless stuff up itself and then calls another main class. The name of the class-to-launch is obtained from the System Properties and program arguments are passed to the `main` of the class-to-launch without changing.

Extract `libs` folder from `projector-server-VERSION.zip` to add it to classpath later.

To launch your app, change your run script like this:
```Shell Script
java \
 -classpath YOUR_USUAL_CLASSPATH:libs/* \
 -Dorg.jetbrains.projector.server.classToLaunch=YOUR_USUAL_MAIN_CLASS \
 org.jetbrains.projector.server.ProjectorLauncher \
 YOUR_USUAL_MAIN_ARGUMENTS
```

As you see, you should add the `libs` folder to you classpath. Also, you should change the main class to the `ProjectorLauncher` but pass your original main class as a special System Property.

We have an example in our demo app called [projector-demo](https://github.com/JetBrains/projector-demo).

Also, we've tested this variant with IntelliJ IDEA. Just download it from [the download page](https://www.jetbrains.com/idea/download/index.html) and only change the `idea.sh` script. In the end of default script, the are lines like the following:
```shell script
...
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jnr-posix-3.0.50.jar"

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
# shellcheck disable=SC2086
"$JAVA_BIN" \
  -classpath "$CLASS_PATH" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_idea_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_idea_.hprof" \
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \
  ${IDE_PROPERTIES_PROPERTY} \
  -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader -Didea.strict.classpath=true -Didea.vendor.name=JetBrains -Didea.paths.selector=IdeaIC2022.1 -Didea.platform.prefix=Idea -Didea.jre.check=true -Dsplash=true \
  com.intellij.idea.Main \
  "$@"
```

You should change them to:
```shell script
...
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jnr-posix-3.0.50.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/projector-server-<server-version>/lib/*"

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
# shellcheck disable=SC2086
"$JAVA_BIN" \
  -classpath "$CLASS_PATH" --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_idea_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_idea_.hprof" \
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \
  ${IDE_PROPERTIES_PROPERTY} \
  -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader -Didea.strict.classpath=true -Didea.vendor.name=JetBrains -Didea.paths.selector=IntelliJIdea2022.1 -Didea.jre.check=true -Dsplash=true \
  -Dorg.jetbrains.projector.server.classToLaunch=com.intellij.idea.Main org.jetbrains.projector.server.ProjectorLauncher \
  "$@"
```

Don't forget to place JARs from `projector-server` distribution to `$IDE_HOME/projector-server-<server-version>/lib`.

Also, you can find this example in [projector-docker](https://github.com/JetBrains/projector-docker) where these actions are done automatically.

### Modifying your application code
Using this way, you can add a custom condition to start the server.

Add a dependency to the `projector-server` project to your app. In the **beginning** of your `main`, decide if you want to run the app headlessly. If yes, invoke `System.setProperty("org.jetbrains.projector.server.enable", "true")` and call the `runProjectorServer` method of the `ProjectorLauncher`.

When you go this way, ensure that no AWT nor Swing operations are performed before the initialization of the server. Such operations can cause some lazy operations of AWT happen and our server doesn't support that.

This way is demonstrated in [projector-demo](https://github.com/JetBrains/projector-demo) too.

### Run with Gradle tasks
There are two gradle tasks for running server. They are handy when developing. To enable them, you should set some properties in `local.properties` file in the project root. Use [local.properties.example](local.properties.example) as a reference.

1. `runServer` &mdash; launch your app with Projector Server. Required properties:
    * `projectorLauncher.targetClassPath` &mdash; classpath of your application;
    * `projectorLauncher.classToLaunch` &mdash; FQN of your application main class.

2. `runIdeaServer` &mdash; launch IntelliJ IDEA with Projector Server. Required property:
    * `projectorLauncher.ideaPath` &mdash; path to IDEA's root directory.

## Connection from browser
When the server is launched, you can open `localhost:8887` in the browser to access the app.

## Notes
Currently, `projector-server` supports only Linux and JetBrains Runtime 11 and 17 as JRE.

To set the port which will be used by Projector Server for WebSocket, use the `-Dorg.jetbrains.projector.server.port=<port-number>` System Property.

## Contributing
[CONTRIBUTING.md](./docs/CONTRIBUTING.md).

## License
[GPLv2+CPE](LICENSE.txt).
