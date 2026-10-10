package dev.journeyscope.sample.shop.data

import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.sample.shop.LatencyProfile
import dev.journeyscope.sample.shop.SampleScenario
import dev.journeyscope.sample.shop.SimulatedNetworkException
import dev.journeyscope.sample.shop.simulatedMetadata
import dev.journeyscope.sample.shop.traced
import kotlinx.coroutines.delay

data class CartApiResponse(val accepted: Boolean, val rejectionReason: String?, val quantity: Int)

interface CartRemoteDataSource {
    suspend fun addItem(productId: String, quantity: Int, scenario: SampleScenario): CartApiResponse
}

/** No real HTTP: latency comes from [LatencyProfile] and the scenario decides the outcome. */
class SimulatedCartApi(private val latency: LatencyProfile = LatencyProfile.Default) : CartRemoteDataSource {
    override suspend fun addItem(productId: String, quantity: Int, scenario: SampleScenario): CartApiResponse =
        traced("POST /cart/items", SpanCategory.NETWORK, simulatedMetadata("endpoint" to "/cart/items", "method" to "POST")) {
            delay(latency.networkFor(scenario))
            when (scenario) {
                SampleScenario.NetworkException -> throw SimulatedNetworkException()
                SampleScenario.BusinessFailure -> CartApiResponse(accepted = false, rejectionReason = "OUT_OF_STOCK", quantity = 0)
                else -> CartApiResponse(accepted = true, rejectionReason = null, quantity = quantity)
            }
        }
}
