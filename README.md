# projector-server
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Tests status badge](https://github.com/JetBrains/projector-server/workflows/Tests/badge.svg)](https://github.com/JetBrains/projector-server/actions)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FJetBrains%2Fprojector-server.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FJetBrains%2Fprojector-server?ref=badge_shield)

Server-side library for running Swing applications remotely.

[Documentation](https://jetbrains.github.io/projector-client/mkdocs/latest/) | [Issue tracker](https://youtrack.jetbrains.com/issues/PRJ)

## Building
The following command creates a zip file with the whole runtime classpath:

```shell script
./gradlew :projector-server:distZip
```

You can find the file here: `projector-server/build/distibution/projector-server-VERSION.zip`.

By default, a proper revision of `projector-client:projector-common` at GitHub will be used as a dependency. If you want to use local `projector-client`, please specify a special local property. You can find an example in [local.properties.example](local.properties.example) file.

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
"$JAVA_BIN" \
  -classpath "$CLASSPATH" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_IDEA_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_IDEA.hprof" \
  -Didea.paths.selector=IdeaIC2019.3 \
  "-Djb.vmOptionsFile=$VM_OPTIONS_FILE" \
  ${IDE_PROPERTIES_PROPERTY} \
  -Didea.platform.prefix=Idea -Didea.jre.check=true \
  com.intellij.idea.Main \
  "$@"
```

You should change them to:
```shell script
"$JAVA_BIN" \
  -classpath "$CLASSPATH:$IDE_HOME/projector-server/lib/*" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_IDEA_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_IDEA.hprof" \
  -Didea.paths.selector=IdeaIC2019.3 \
  "-Djb.vmOptionsFile=$VM_OPTIONS_FILE" \
  ${IDE_PROPERTIES_PROPERTY} \
  -Didea.platform.prefix=Idea -Didea.jre.check=true \
  -Dorg.jetbrains.projector.server.classToLaunch=com.intellij.idea.Main \
  org.jetbrains.projector.server.ProjectorLauncher \
  "$@"
```

Don't forget to place JARs from `projector-server` distribution to `$IDE_HOME/projector-server/lib`.

Also, you can find this example in [projector-docker](https://github.com/JetBrains/projector-docker) where these actions are done automatically.

### Modifying your application code
Using this way, you can add a custom condition to start the server.

Add a dependency to the `projector-server` project to your app. In the **beginning** of your `main`, decide if you want to run the app headlessly. If yes, invoke `System.setProperty("org.jetbrains.projector.server.enable", "true")` and call the `startServer` method of the `HeadlessServer`.

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
Currently, `projector-server` supports only Linux and JetBrains Runtime 11 as JRE.

To set the port which will be used by Projector Server for WebSocket, use the `-Dorg.jetbrains.projector.server.port=8001` System Property.

## Contributing
[CONTRIBUTING.md](https://github.com/JetBrains/projector-server/blob/master/docs/CONTRIBUTING.md).

## License
[GPLv2+CPE](LICENSE.txt).


[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FJetBrains%2Fprojector-server.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FJetBrains%2Fprojector-server?ref=badge_large)