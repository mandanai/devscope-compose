package dev.chayaphon.journeyscope.core.network

import dev.chayaphon.journeyscope.core.RedactionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkSanitizerTest {
    private val policy = RedactionPolicy.Default

    private fun url(
        raw: String,
        allowed: Set<String> = emptySet(),
    ) = UrlSanitizer.sanitize(raw, allowed, policy)

    @Test
    fun stripsUserInfoFragmentAndQueryValues() {
        val sanitized = url("https://user:pw@api.shop.com/cart/items?page=2&token=abc#frag")
        assertEquals("api.shop.com", sanitized.host)
        assertEquals("/cart/items", sanitized.path)
        assertEquals(listOf("page", "token"), sanitized.queryKeys)
        assertTrue(sanitized.allowedQueryValues.isEmpty())
    }

    @Test
    fun portIsSeparatedFromHost() {
        assertEquals("localhost", url("http://localhost:8080/a").host)
        assertEquals(8080, url("http://localhost:8080/a").port)
        assertEquals("[::1]", url("http://[::1]:9/a").host)
        assertEquals(null, url("https://a.com/x").port)
    }

    @Test
    fun allowlistedQueryValueIsKeptButSensitiveKeysNever() {
        val sanitized = url("https://api.shop.com/search?page=2&token=abc", allowed = setOf("page", "token"))
        assertEquals(mapOf("page" to "2"), sanitized.allowedQueryValues)
    }

    @Test
    fun presignedUrlDropsEveryQueryParameter() {
        val sanitized =
            url(
                "https://bucket.s3.amazonaws.com/a/b.png?X-Amz-Algorithm=AWS4&X-Amz-Credential=AKIA%2F1&X-Amz-Signature=deadbeef&page=1",
                allowed = setOf("page"),
            )
        assertTrue(sanitized.signed)
        assertTrue(sanitized.queryKeys.isEmpty())
        assertTrue(sanitized.allowedQueryValues.isEmpty())
    }

    @Test
    fun opaquePathSegmentsAreMasked() {
        assertEquals("/users/{id}/orders/42", url("https://a.com/users/123e4567-e89b-12d3-a456-426614174000/orders/42").path)
        assertEquals("/files/{id}", url("https://a.com/files/9f8a7b6c5d4e3f2a1b0c9d8e").path)
        assertEquals("/v1/cart", url("https://a.com/v1/cart").path)
    }

    @Test
    fun headersAreAllowlistOnlyAndCredentialsStayDenied() {
        val headers =
            mapOf(
                "Authorization" to listOf("Bearer secret"),
                "Cookie" to listOf("sid=1"),
                "X-Request-Id" to listOf("r-1"),
                "Accept" to listOf("application/json"),
            )
        val collected = HeaderSanitizer.collect("h.", headers, setOf("authorization", "cookie", "x-request-id"), policy)
        assertEquals(mapOf("h.x-request-id" to "r-1"), collected)
        assertTrue(HeaderSanitizer.collect("h.", headers, emptySet(), policy).isEmpty())
    }

    @Test
    fun propagationTrustsExactAndWildcardHostsOverHttpsOnly() {
        val config = PropagationConfig(setOf("api.shop.com", "*.internal.shop.com"))
        assertTrue(config.trusts("https", "api.shop.com"))
        assertTrue(config.trusts("https", "svc.internal.shop.com"))
        assertFalse(config.trusts("http", "api.shop.com"))
        assertFalse(config.trusts("https", "evilshop.com"))
        assertFalse(config.trusts("https", "api.shop.com.evil.io"))
        assertFalse(config.trusts("https", "internal.shop.com"))
    }

    @Test
    fun classifiesFailuresByCauseChain() {
        class SocketTimeoutException : Exception()

        class UnknownHostException : Exception()

        class SSLHandshakeException : Exception()

        class ConnectException : Exception()
        assertEquals(NetworkFailure.TIMEOUT, FailureClassifier.forThrowable(SocketTimeoutException(), cancelled = false))
        assertEquals(NetworkFailure.DNS, FailureClassifier.forThrowable(Exception(UnknownHostException()), cancelled = false))
        assertEquals(NetworkFailure.TLS, FailureClassifier.forThrowable(SSLHandshakeException(), cancelled = false))
        assertEquals(NetworkFailure.CONNECTION, FailureClassifier.forThrowable(ConnectException(), cancelled = false))
        assertEquals(NetworkFailure.CANCELLED, FailureClassifier.forThrowable(ConnectException(), cancelled = true))
        assertEquals(NetworkFailure.UNKNOWN, FailureClassifier.forThrowable(IllegalStateException(), cancelled = false))
        assertEquals(NetworkFailure.HTTP_CLIENT_ERROR, FailureClassifier.forStatus(404))
        assertEquals(NetworkFailure.HTTP_SERVER_ERROR, FailureClassifier.forStatus(503))
        assertEquals(null, FailureClassifier.forStatus(200))
    }
}
