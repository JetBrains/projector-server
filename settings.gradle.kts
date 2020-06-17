import java.util.*

pluginManagement {
  val kotlinVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinVersion apply false
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
    }
  }
}

if (localProperties["projectorLauncher.ideaPath"] != null) {
  includeBuild("../projector-markdown-plugin")
}

include("projector-awt")
include("projector-server")
