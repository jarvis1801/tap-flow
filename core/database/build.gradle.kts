plugins {
  alias(libs.plugins.tapflow.android.library)
  alias(libs.plugins.tapflow.android.hilt)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.tapflow.core.database"
}

dependencies {
  implementation(project(":core:model"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.runtime.annotation)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
}
