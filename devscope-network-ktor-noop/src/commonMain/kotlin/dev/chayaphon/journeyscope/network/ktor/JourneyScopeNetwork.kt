package dev.chayaphon.journeyscope.network.ktor

import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.network.NetworkTraceConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin

public class JourneyScopeNetworkConfig {
    public var trace: NetworkTraceConfig = NetworkTraceConfig()
    public var tracer: () -> JourneyTracer = { JourneyScope.tracer }
}

/** Release-build twin: installs without intercepting any request. */
public val JourneyScopeNetwork: ClientPlugin<JourneyScopeNetworkConfig> =
    createClientPlugin("JourneyScopeNetwork", ::JourneyScopeNetworkConfig) {}
