import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
  kotlin("jvm") apply false
}

val kotlinVersion: String by project
val targetJvm: String by project

subprojects {
  group = "org.jetbrains"
  version = "1.0-SNAPSHOT"

  repositories {
    jcenter()
  }

  tasks.withType<JavaCompile> {
    sourceCompatibility = targetJvm
  }

  tasks.withType<KotlinCompile<*>> {
    kotlinOptions {
      freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
  }

  tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
      jvmTarget = targetJvm
    }
  }
}
