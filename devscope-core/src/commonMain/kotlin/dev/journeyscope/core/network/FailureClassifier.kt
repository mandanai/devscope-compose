package dev.journeyscope.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

internal object FailureClassifier {
    private const val MAX_CAUSE_DEPTH = 8

    fun forStatus(statusCode: Int): NetworkFailure? = when (statusCode) {
        in 400..499 -> NetworkFailure.HTTP_CLIENT_ERROR
        in 500..599 -> NetworkFailure.HTTP_SERVER_ERROR
        else -> null
    }

    fun forThrowable(throwable: Throwable, cancelled: Boolean): NetworkFailure {
        if (cancelled) return NetworkFailure.CANCELLED
        val names = generateSequence(throwable) { it.cause }.take(MAX_CAUSE_DEPTH).map { it::class.simpleName.orEmpty().lowercase() }.toList()
        return when {
            throwable is TimeoutCancellationException || names.any { "timeout" in it || "timedout" in it } -> NetworkFailure.TIMEOUT
            throwable is CancellationException -> NetworkFailure.CANCELLED
            names.any { "unknownhost" in it || "dns" in it || "unresolved" in it || "nodename" in it } -> NetworkFailure.DNS
            names.any { "ssl" in it || "tls" in it || "certificate" in it || "handshake" in it } -> NetworkFailure.TLS
            names.any { "protocol" in it } -> NetworkFailure.PROTOCOL
            names.any { "connect" in it || "socket" in it || "eof" in it || "ioexception" in it || "unreachable" in it } -> NetworkFailure.CONNECTION
            else -> NetworkFailure.UNKNOWN
        }
    }
}
