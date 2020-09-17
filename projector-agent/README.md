# projector-agent
This subproject allows draw application window and send it with server at the same time. 

## Building
This will give you a jar:

```shell script
./gradlew :projector-agent:jar
```

This command creates a jar in the `projector-agent/build/libs` dir.

## How to run my application using this?
To launch your app, change your run script like this:
```Shell Script
java \
-javaagent:PATH_TO_AGENT_JAR=PATH_TO_AGENT_JAR \
YOUR_USUAL_JAVA_ARGUMENTS
```

### Run with Gradle tasks
There are two gradle tasks for running app with server:

1. `runWithAgent` - launch your app with projector server. For enabling this task set properties in `local.properties` file in the project root:
    * `projectorLauncher.targetClassPath` - classPath of your application;
    * `projectorLauncher.classToLaunch` - your application main class.
    
2. `runIdeaWithAgent` - launch IDEA with projector server. For enabling this task set properties in `local.properties` file in the project root:
    * `projectorLauncher.ideaPath` - path to IDEA root directory.
