plugins {
  alias(libs.plugins.tapflow.jvm.library)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.compose.runtime.annotation)
  testImplementation(libs.junit)
}
