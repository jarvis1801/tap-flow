import com.tapflow.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply {
        apply("tapflow.android.library")
        apply("tapflow.android.library.compose")
        apply("tapflow.android.hilt")
        apply("org.jetbrains.kotlin.plugin.serialization")
      }

      dependencies {
        add("implementation", project(":core:model"))
        add("implementation", libs.findLibrary("androidx-core-ktx").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-material-icons-extended").get())
        add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
        add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
        add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
        add("implementation", libs.findLibrary("androidx-navigation-compose").get())
        add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
      }
    }
  }
}
