plugins {
  alias(libs.plugins.tapflow.android.feature)
}

android {
  namespace = "com.tapflow.feature.overlay"
}

dependencies {
  implementation(project(":core:database"))
  implementation(project(":core:datastore"))
  implementation(project(":core:service"))
}
