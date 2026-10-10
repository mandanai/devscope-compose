import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

open class KmpLibraryConventionPlugin : Plugin<Project> {
    protected open val includeIos: Boolean = true

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            explicitApi()
            @OptIn(ExperimentalAbiValidation::class)
            abiValidation { }
            val level = KotlinVersion.fromVersion(libs.version("kotlinLanguage"))
            compilerOptions {
                languageVersion.set(level)
                apiVersion.set(level)
                freeCompilerArgs.add("-Xsuppress-version-warnings")
            }
            configureKotlinMultiplatform(this, androidNamespace(), includeIos)
        }
    }
}

class AndroidKmpLibraryConventionPlugin : KmpLibraryConventionPlugin() {
    override val includeIos: Boolean = false
}
