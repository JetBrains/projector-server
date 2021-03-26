# projector-server
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Tests status badge](https://github.com/JetBrains/projector-server/workflows/Tests/badge.svg)](https://github.com/JetBrains/projector-server/actions)

通过网络远程运行 Swing 应用的服务器端库。

[文档](https://jetbrains.github.io/projector-client/mkdocs/latest/) | [Issue tracker](https://youtrack.jetbrains.com/issues/PRJ)

## 构建

下面的命令创建一个包含整个运行时类路径的 zip 文件:

```shell script
./gradlew :projector-server:distZip
```

你可以在这里: `projector-server/build/distibution/projector-server-VERSION.zip` 找到文件。

默认情况下，GitHub上的 'projector-client:projector-common' 的正确版本将被用作一个依赖项。如果你想使用本地 `projector-client`，请指定一个特殊的本地属性。 您可以在[local.properties.example](local.properties.example)文件中找到示例。

## 如何使用这个运行我的应用程序?

一共有 2 种方法。

### 不修改应用程序代码

这是推荐的方式。如果你没有特殊要求，你可以使用它。这样的话你根本不需要重建你的应用程序。

在 `projector-server` 项目中，有一个 `ProjectorLauncher` 主类。它自己设置无头的东西(译者注:[什么是无头](https://zh.wikipedia.org/wiki/%E6%97%A0%E5%A4%B4%E6%B5%8F%E8%A7%88%E5%99%A8))，然后调用另一个主类。要启动的类的名称是从系统属性中获取的，而程序参数会被传递到要启动类的 `main` ，而不需要做任何改变。

从 `projector-server-VERSION.zip` 中提取 `libs` 文件夹。将它添加到类路径中。

要启动你的应用程序，修改你的运行脚本如下:

```Shell Script
java \
-classpath YOUR_USUAL_CLASSPATH:libs/* \
-Dorg.jetbrains.projector.server.classToLaunch=YOUR_USUAL_MAIN_CLASS \
org.jetbrains.projector.server.ProjectorLauncher \
YOUR_USUAL_MAIN_ARGUMENTS
```

如您所见，您应该将 `libs` 文件夹添加到类路径中。同样，你应该将main类更改为 `ProjectorLauncher` ，但将原始的main类作为一个特殊的系统属性传递。


在我们的演示应用中有一个叫做[projector-demo](https://github.com/JetBrains/projector-demo)的例子。

同时，我们也用 IntelliJ IDEA 测试了这个变体。只要从[下载页面](https://www.jetbrains.com/idea/download/index.html)下载它，只改变 `idea.sh` 脚本。在默认脚本的末尾，如下所示:


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

你应该把它们改为:

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

不要忘记把 jar 从 `projector-server` 指定到 `$IDE_HOME/projector-server/lib`。


同样，你可以在[projector-docker](https://github.com/JetBrains/projector-docker)中找到这个例子，这些操作都是自动完成的。

### 修改应用程序代码

使用这种方式，您可以添加一个自定义条件来启动服务器。

给你的应用程序添加一个 `projector-server` 项目的依赖项。在 `main` 的**开头**中，决定你是否想要无头运行应用程序。如果是，调用`System.setProperty("org.jetbrains.projector.server.enable", "true")` 并调用 `HeadlessServer` 的 `startServer` 方法。


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
