import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            explicitApi()
            val level = KotlinVersion.fromVersion(libs.version("kotlinLanguage"))
            compilerOptions {
                languageVersion.set(level)
                apiVersion.set(level)
                freeCompilerArgs.add("-Xsuppress-version-warnings")
            }
            configureKotlinMultiplatform(this, androidNamespace())
        }
    }
}
