plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(11)
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.compose.runtime.annotation)
  testImplementation(libs.junit)
}
