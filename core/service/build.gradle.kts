plugins {
  alias(libs.plugins.tapflow.android.library)
  alias(libs.plugins.tapflow.android.library.compose)
  alias(libs.plugins.tapflow.android.hilt)
}

android {
  namespace = "com.tapflow.core.service"

  buildFeatures {
    aidl = true
  }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:database"))
  implementation(project(":core:datastore"))
  implementation(project(":core:billing"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.runtime.annotation)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
