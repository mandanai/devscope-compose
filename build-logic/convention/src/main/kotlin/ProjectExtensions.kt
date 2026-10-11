import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun Project.androidNamespace(prefix: String = "dev.chayaphon.journeyscope"): String =
    "$prefix." + path.removePrefix(":")
        .replace("devscope-", "")
        .split(':', '-')
        .joinToString(".") { it.lowercase() }
        .replace(Regex("[^a-z0-9.]"), "")
