plugins {
  alias(libs.plugins.tapflow.android.library)
  alias(libs.plugins.tapflow.android.hilt)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.tapflow.core.server"
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:database"))
  implementation(project(":core:datastore"))
  implementation(project(":core:billing"))
  implementation(project(":core:service"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.runtime.annotation)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.websockets)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
