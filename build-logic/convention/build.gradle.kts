import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
}

group = "com.tapflow.buildlogic"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.compose.gradlePlugin)
  compileOnly(libs.ksp.gradlePlugin)
  compileOnly(libs.hilt.gradlePlugin)
  compileOnly(libs.kotlin.serialization.gradlePlugin)
}

tasks {
  validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
  }
}

gradlePlugin {
  plugins {
    register("jvmLibrary") {
      id = "tapflow.jvm.library"
      implementationClass = "JvmLibraryConventionPlugin"
    }
    register("androidApplication") {
      id = "tapflow.android.application"
      implementationClass = "AndroidApplicationConventionPlugin"
    }
    register("androidApplicationCompose") {
      id = "tapflow.android.application.compose"
      implementationClass = "AndroidApplicationComposeConventionPlugin"
    }
    register("androidLibrary") {
      id = "tapflow.android.library"
      implementationClass = "AndroidLibraryConventionPlugin"
    }
    register("androidLibraryCompose") {
      id = "tapflow.android.library.compose"
      implementationClass = "AndroidLibraryComposeConventionPlugin"
    }
    register("androidHilt") {
      id = "tapflow.android.hilt"
      implementationClass = "HiltConventionPlugin"
    }
    register("androidFeature") {
      id = "tapflow.android.feature"
      implementationClass = "AndroidFeatureConventionPlugin"
    }
  }
}
