pluginManagement {
  val kotlinVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinVersion apply false
  }
}

rootProject.name = "projector-server"

includeBuild("../projector-client")
includeBuild("../projector-markdown-plugin")

include("projector-awt")
include("projector-server")
