package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.CentralRegistryProperties;
import in.org.projecteka.hiu.clients.CentralRegistryClient;
import in.org.projecteka.hiu.clients.Token;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class CentralRegistry {
    private final CentralRegistryProperties centralRegistryProperties;
    private final CentralRegistryClient centralRegistryClient;
    private final Logger logger = Logger.getLogger(CentralRegistry.class);

    public Mono<String> token() {
        return centralRegistryClient.getTokenFor(
                centralRegistryProperties.getClientId(),
                centralRegistryProperties.getClientSecret())
                .doOnError(logger::error)
                .map(Token::getBearerToken);
    }
}
