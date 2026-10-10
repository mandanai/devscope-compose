package dev.journeyscope.sample.shop.data

import dev.journeyscope.sample.shop.SampleScenario
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

data class CartApiResponse(val accepted: Boolean, val rejectionReason: String?, val quantity: Int)

interface CartRemoteDataSource {
    suspend fun addItem(productId: String, quantity: Int, scenario: SampleScenario): CartApiResponse
}

/** The NETWORK span comes from the `JourneyScopeNetwork` plugin installed on [client]; nothing here traces by hand. */
class KtorCartApi(private val client: HttpClient) : CartRemoteDataSource {
    override suspend fun addItem(productId: String, quantity: Int, scenario: SampleScenario): CartApiResponse {
        val body = client.post("$BASE_URL/cart/items") {
            contentType(ContentType.Application.Json)
            header(SCENARIO_HEADER, scenario.name)
            setBody("""{"productId":"$productId","quantity":$quantity}""")
        }.bodyAsText()
        val code = jsonString(body, "returnCode")
        return CartApiResponse(
            accepted = code == SUCCESS_CODE,
            rejectionReason = jsonString(body, "reason"),
            quantity = Regex("\"quantity\":(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: 0,
        )
    }

    companion object {
        const val BASE_URL = "https://api.shop.example"
        const val SCENARIO_HEADER = "X-Sample-Scenario"
        const val SUCCESS_CODE = "0000"
    }
}

internal fun jsonString(json: String, key: String): String? = Regex("\"$key\":\"([^\"]*)\"").find(json)?.groupValues?.get(1)
