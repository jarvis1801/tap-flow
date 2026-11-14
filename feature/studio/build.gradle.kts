plugins {
  alias(libs.plugins.tapflow.android.feature)
}

android {
  namespace = "com.tapflow.feature.studio"
}

dependencies {
  implementation(project(":core:database"))
  implementation(project(":core:billing"))
  implementation(libs.coil.compose)
  testImplementation(libs.junit)
}
