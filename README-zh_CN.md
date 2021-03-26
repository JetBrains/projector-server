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

默认情况下，GitHub上的 'projector-client:projector-common' 的正确版本将被用作一个依赖项。如果你想使用本地 `projector-client`，请指定一个特殊的本地属性。 您可以在 [local.properties.example](local.properties.example) 文件中找到示例。

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


在我们的演示应用中有一个叫做 [projector-demo](https://github.com/JetBrains/projector-demo) 的例子。

同时，我们也用 IntelliJ IDEA 测试了这个变体。只要从 [下载页面](https://www.jetbrains.com/idea/download/index.html) 下载它，只改变 `idea.sh` 脚本。在默认脚本的末尾，如下所示:


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


当您采用这种方式时，请确保在服务器初始化之前没有执行任何AWT或Swing操作。这样的操作可能会导致AWT的一些惰性操作发生，而我们的服务器不支持这种操作。

这种方法也在 [projector-demo](https://github.com/JetBrains/projector-demo) 中得到了演示。

### 使用Gradle任务运行

有两个 gradle 任务用于运行server。在开发时使用它们会很方便。要启用它们，你应该在项目根目录下的属性文件 `local.properties` 中设置一些属性。你可以参考 [local.properties.example](local.properties.example) 。

1. `runServer` &mdash; 用 Projector Server 启动你的应用程序。必需的属性:
    * `projectorLauncher.targetClassPath` &mdash; 应用程序的类路径;
    * `projectorLauncher.classToLaunch` &mdash; 应用程序主类的[FQN](https://en.wikipedia.org/wiki/Fully_qualified_name).

2. `runIdeaServer` &mdash; 使用 Projector Server 启动IntelliJ IDEA 。 必需的属性:
    * `projectorLauncher.ideaPath` &mdash; IDEA 的根目录路径.

## 通过浏览器进行连接

当服务器启动后，可以在浏览器中打开 `localhost:8887` 访问应用。

## 注意

目前，`projector-server` 只支持 Linux 和 JetBrains 运行时 11 版本的 JRE。

要为 Projector Server 设置将要使用的端口，可以使用 `-Dorg.jetbrains.projector.server.port=8001` 的系统属性。


## 贡献

[CONTRIBUTING.md](https://github.com/JetBrains/projector-server/blob/master/docs/CONTRIBUTING.md).

## 版权说明

[GPLv2+CPE](LICENSE.txt).
