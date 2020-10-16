# projector-agent
This subproject allows draw application window and send it with projector-server at the same time. 

## Building
This will give you a jar in the `projector-agent/build/libs` dir:
```shell script
./gradlew :projector-agent:jar
```

## How to run my application using this?
### Not modifying your application code
To launch your app, change your run script like this:
```Shell Script
java \
-Djdk.attach.allowAttachSelf=true \
-Dswing.bufferPerWindow=false \
-Dorg.jetbrains.projector.agent.path=PATH_TO_AGENT_JAR \
-Dorg.jetbrains.projector.agent.classToLaunch=FQN_OF_YOUR_MAIN_CLASS \
-classpath YOUR_CLASSPATH:PATH_TO_AGENT_JAR \
org.jetbrains.projector.agent.AgentLauncher \
YOUR_MAIN_ARGUMENTS
```

### Modifying your application code
You can launch sharing of your application dynamically from your code, just call `AgentLauncher.attachAgent` method.

### Run with Gradle tasks
There are two gradle tasks for running server. They are handy when developing. To enable them, you should set some properties in `local.properties` file in the project root. Use [local.properties.example](../local.properties.example) as a reference.

1. `runWithAgent` &mdash; launch your app with Projector Server. Required properties:
    * `projectorLauncher.targetClassPath` &mdash; classPath of your application;
    * `projectorLauncher.classToLaunch` &mdash; your application main class.

2. `runIdeaWithAgent` &mdash; launch IntelliJ IDEA with Projector Server. Required property:
    * `projectorLauncher.ideaPath` &mdash; path to IDEA's root directory.
