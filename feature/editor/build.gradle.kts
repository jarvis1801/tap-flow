plugins {
  alias(libs.plugins.tapflow.android.feature)
}

android {
  namespace = "com.tapflow.feature.editor"
}

dependencies {
  implementation(project(":core:database"))
  implementation(project(":core:datastore"))
  implementation(project(":core:billing"))
  implementation(project(":core:service"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.coil.compose)
  testImplementation(libs.junit)
}
