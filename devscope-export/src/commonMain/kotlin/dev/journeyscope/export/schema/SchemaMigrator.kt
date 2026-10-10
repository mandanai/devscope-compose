package dev.journeyscope.export.schema

import dev.journeyscope.export.json.JsonValue

/** Upgrades older documents one version at a time; unknown or newer versions are never guessed at. */
public class SchemaMigrator(
    private val currentVersion: Int = TraceSchema.VERSION,
    private val migrations: Map<Int, (JsonValue.Obj) -> JsonValue.Obj> = emptyMap(),
) {
    /** Whether a document could be brought to the current schema version. */
    public sealed class Outcome {
        public data class Ready(
            val document: JsonValue.Obj,
        ) : Outcome()

        public data class Unsupported(
            val reason: String,
        ) : Outcome()
    }

    public fun migrate(document: JsonValue.Obj): Outcome {
        val declared =
            (document.fields[Keys.VERSION] as? JsonValue.Integer)?.value
                ?: return Outcome.Unsupported("missing ${Keys.VERSION}")
        if (declared < 1) return Outcome.Unsupported("invalid schema version $declared")
        if (declared > currentVersion) return Outcome.Unsupported("schema version $declared is newer than supported $currentVersion")
        var version = declared.toInt()
        var current = document
        while (version < currentVersion) {
            val step = migrations[version] ?: return Outcome.Unsupported("no migration from version $version")
            current = JsonValue.Obj(step(current).fields + (Keys.VERSION to JsonValue.Integer((version + 1).toLong())))
            version++
        }
        return Outcome.Ready(current)
    }
}
