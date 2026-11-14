plugins {
  alias(libs.plugins.tapflow.android.feature)
}

android {
  namespace = "com.tapflow.feature.paywall"
}

dependencies {
  implementation(project(":core:billing"))
  implementation(project(":core:datastore"))
  implementation(libs.androidx.activity.compose)
}
