import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("maven-publish")

        group = property("GROUP").toString()
        version = property("VERSION_NAME").toString()

        extensions.configure<PublishingExtension> {
            publications.withType(MavenPublication::class.java).configureEach {
                pom {
                    name.set(project.name)
                    url.set(property("POM_URL").toString())
                    licenses {
                        license {
                            name.set(property("POM_LICENSE_NAME").toString())
                            url.set(property("POM_LICENSE_URL").toString())
                        }
                    }
                    scm { url.set(property("POM_SCM_URL").toString()) }
                    developers { developer { name.set(property("POM_DEVELOPER_NAME").toString()) } }
                }
            }
        }
    }
}
