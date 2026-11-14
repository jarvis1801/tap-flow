plugins {
  alias(libs.plugins.tapflow.android.library)
  alias(libs.plugins.tapflow.android.hilt)
}

android {
  namespace = "com.tapflow.core.billing"
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:datastore"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.runtime.annotation)
  implementation(libs.billing.ktx)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
}
