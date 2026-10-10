import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class SampleAndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("journeyscope.compose")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.version("androidCompileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("androidMinSdk").toInt()
                targetSdk = libs.version("androidCompileSdk").toInt()
                versionCode = 1
                versionName = "1.0"
            }
            compileOptions {
                val java = JavaVersion.toVersion(libs.version("jdk"))
                sourceCompatibility = java
                targetCompatibility = java
            }
        }
    }
}
