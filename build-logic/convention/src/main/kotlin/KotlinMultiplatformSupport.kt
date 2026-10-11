import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureKotlinMultiplatform(kotlin: KotlinMultiplatformExtension, namespace: String, ios: Boolean = true) {
    val jdk = libs.version("jdk").toInt()
    kotlin.jvmToolchain(jdk)
    kotlin.compilerOptions.allWarningsAsErrors.set(true)

    kotlin.extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
        this.namespace = namespace
        compileSdk = libs.version("androidCompileSdk").toInt()
        minSdk = libs.version("androidMinSdk").toInt()
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jdk.toString()))
        withHostTest { }
    }

    if (ios) {
        kotlin.iosArm64()
        kotlin.iosSimulatorArm64()
    }

    kotlin.sourceSets.getByName("commonTest").dependencies {
        implementation(libs.findLibrary("kotlin-test").get())
        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
