import org.gradle.api.Plugin
import org.gradle.api.Project

/** Makes `devscope-x-noop` claim the capability of `devscope-x`, so Gradle refuses both on one classpath. */
class NoopConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val realName = name.removeSuffix("-noop")
        afterEvaluate {
            configurations.matching { it.isCanBeConsumed && !it.isCanBeResolved }.configureEach {
                outgoing.capability("${project.group}:$realName:${project.version}")
                outgoing.capability("${project.group}:${project.name}:${project.version}")
            }
        }
    }
}
