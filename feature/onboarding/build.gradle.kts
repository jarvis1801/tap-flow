plugins {
  alias(libs.plugins.tapflow.android.feature)
}

android {
  namespace = "com.tapflow.feature.onboarding"
}

dependencies {
  implementation(project(":core:datastore"))
  implementation(project(":core:service"))
  implementation(libs.androidx.activity.compose)
}
