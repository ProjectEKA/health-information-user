package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ClientError;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static java.lang.String.format;

@AllArgsConstructor
public class CentralRegistryClient {
    private final WebClient.Builder builder;
    private final Logger logger = Logger.getLogger(CentralRegistryClient.class);

    public Mono<Token> getTokenFor(String clientId, String clientSecret) {
        return builder.build()
                .post()
                .uri("/api/1.0/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestWith(clientId, clientSecret)))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(Properties.class)
                        .doOnNext(properties -> logger.error(properties.toString()))
                        .thenReturn(ClientError.authenticationFailed()))
                .bodyToMono(Properties.class)
                .map(properties -> new Token(format("Bearer %s", properties.getProperty("accessToken"))));
    }

    private SessionRequest requestWith(String clientId, String clientSecret) {
        return new SessionRequest(clientId, clientSecret, "password");
    }

    @AllArgsConstructor
    @Data
    private static class SessionRequest {
        private String clientId;
        private String clientSecret;
        private String grantType;
    }
}