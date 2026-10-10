package dev.journeyscope.core.model

/** Metadata keys shared by adapters, the diagnostic engine and the inspector. */
public object TraceMetadataKeys {
    public const val HTTP_METHOD: String = "http.method"
    public const val HTTP_HOST: String = "http.host"
    public const val HTTP_PATH: String = "http.path"
    public const val HTTP_STATUS: String = "http.status"
    public const val HTTP_FAILURE: String = "http.failure"
    public const val BUSINESS_STATUS: String = "business.status"

    public const val DB_OPERATION: String = "db.operation"
    public const val DB_LABEL: String = "db.label"
    public const val DB_TRANSACTION_ID: String = "db.transaction.id"
    public const val DB_TRANSACTION_OUTCOME: String = "db.transaction.outcome"
    public const val DB_ROWS: String = "db.rows"
}
