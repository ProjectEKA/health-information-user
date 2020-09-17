package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ClientError;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;
import static in.org.projecteka.hiu.common.Constants.GATEWAY_SESSIONS;
import static java.lang.String.format;

@AllArgsConstructor
public class GatewayAuthenticationClient {
    private final WebClient webclient;
    private final Logger logger = Logger.getLogger(GatewayAuthenticationClient.class);

    public GatewayAuthenticationClient(WebClient.Builder webClient, String baseUrl) {
        this.webclient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Token> getTokenFor(String clientId, String clientSecret) {
        return webclient
                .post()
                .uri(GATEWAY_SESSIONS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
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
        return new SessionRequest(clientId, clientSecret);
    }

    @AllArgsConstructor
    @Data
    private static class SessionRequest {
        private String clientId;
        private String clientSecret;
    }
}