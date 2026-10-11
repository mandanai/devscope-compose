import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.vanniktech.maven.publish")

        group = property("GROUP").toString()
        version = property("VERSION_NAME").toString()

        val remoteEnabled = providers.gradleProperty("mavenCentralUsername").isPresent
        val signingEnabled = providers.gradleProperty("signingInMemoryKey").isPresent

        extensions.configure<MavenPublishBaseExtension> {
            if (remoteEnabled) publishToMavenCentral()
            if (signingEnabled) signAllPublications()

            pom {
                name.set(project.name)
                description.set(project.description ?: "JourneyScope ${project.name}: user-journey tracing for Kotlin Multiplatform.")
                inceptionYear.set(property("POM_INCEPTION_YEAR").toString())
                url.set(property("POM_URL").toString())
                licenses {
                    license {
                        name.set(property("POM_LICENSE_NAME").toString())
                        url.set(property("POM_LICENSE_URL").toString())
                        distribution.set(property("POM_LICENSE_URL").toString())
                    }
                }
                scm {
                    url.set(property("POM_SCM_URL").toString())
                    connection.set(property("POM_SCM_CONNECTION").toString())
                    developerConnection.set(property("POM_SCM_DEV_CONNECTION").toString())
                }
                developers {
                    developer {
                        id.set(property("POM_DEVELOPER_ID").toString())
                        name.set(property("POM_DEVELOPER_NAME").toString())
                        url.set(property("POM_DEVELOPER_URL").toString())
                    }
                }
            }
        }
    }
}
