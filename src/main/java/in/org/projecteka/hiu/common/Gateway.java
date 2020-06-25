package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.clients.GatewayAuthenticationClient;
import in.org.projecteka.hiu.clients.Token;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class Gateway {
    private final GatewayProperties gatewayProperties;
    private final GatewayAuthenticationClient gatewayAuthenticationClient;
    private final Logger logger = Logger.getLogger(Gateway.class);

    public Mono<String> token() {
        return gatewayAuthenticationClient.getTokenFor(
                gatewayProperties.getClientId(),
                gatewayProperties.getClientSecret())
                .doOnError(logger::error)
                .map(Token::getBearerToken);
    }
}
