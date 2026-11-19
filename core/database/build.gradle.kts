plugins {
  alias(libs.plugins.tapflow.android.library)
  alias(libs.plugins.tapflow.android.hilt)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.androidx.room3)
}

android {
  namespace = "com.tapflow.core.database"
}

room3 {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(project(":core:model"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.runtime.annotation)
  implementation(libs.androidx.room3.runtime)
  implementation(libs.androidx.sqlite.framework)
  ksp(libs.androidx.room3.compiler)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.room3.testing)
}
