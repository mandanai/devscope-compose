import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SampleLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("journeyscope.compose")

        extensions.configure<KotlinMultiplatformExtension> {
            configureKotlinMultiplatform(this, androidNamespace("dev.journeyscope.sample"))
        }
    }
}
