import java.util.*

pluginManagement {
  val kotlinVersion: String by settings
  val intellijPluginVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinVersion apply false
    id("org.jetbrains.intellij") version intellijPluginVersion apply false
  }
}

rootProject.name = "projector-server"

val localProperties = Properties().apply {
  try {
    load(File(rootDir, "local.properties").inputStream())
  }
  catch (t: Throwable) {
    println("Can't read local.properties: $t, assuming empty")
  }
}

if (localProperties["useLocalProjectorClient"] == "true") {
  includeBuild("../projector-client") {
    dependencySubstitution {
      substitute(module("com.github.JetBrains.projector-client:projector-common")).with(project(":projector-common"))
      substitute(module("com.github.JetBrains.projector-client:projector-server-core")).with(project(":projector-server-core"))
      substitute(module("com.github.JetBrains.projector-client:projector-util-logging")).with(project(":projector-util-logging"))
    }
  }
}

include("projector-agent")
include("projector-awt")
include("projector-plugin")
include("projector-server")
