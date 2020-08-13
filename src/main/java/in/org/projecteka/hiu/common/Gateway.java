package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.clients.GatewayAuthenticationClient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class Gateway {
    private final Logger logger = Logger.getLogger(Gateway.class);

    private final GatewayProperties gatewayProperties;
    private final GatewayAuthenticationClient gatewayAuthenticationClient;
    private final CacheAdapter<String, String> accessTokenCache;

    public Mono<String> token() {
        return accessTokenCache.get("hiu:gateway:accessToken")
                .switchIfEmpty(Mono.defer(this::tokenUsingSecret))
                .doOnError(logger::error)
                .map(token -> token);
    }

    private Mono<String> tokenUsingSecret() {
        return gatewayAuthenticationClient.getTokenFor(gatewayProperties.getClientId(), gatewayProperties.getClientSecret())
                .flatMap(token ->
                        accessTokenCache.put("hiu:gateway:accessToken", token.getBearerToken())
                                .thenReturn(token.getBearerToken()));
    }
}
