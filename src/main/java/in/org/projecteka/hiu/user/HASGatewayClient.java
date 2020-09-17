package in.org.projecteka.hiu.user;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static in.org.projecteka.hiu.ClientError.networkServiceCallFailed;
import static in.org.projecteka.hiu.ClientError.unAuthorized;
import static in.org.projecteka.hiu.common.Constants.GATEWAY_SESSIONS;
import static org.springframework.http.MediaType.APPLICATION_JSON;

public class HASGatewayClient {
    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(HASGatewayClient.class);

    public HASGatewayClient(WebClient.Builder webClient, String baseUrl) {
        this.webClient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Session> getToken(String clientId, String clientSecret) {
        return webClient
                .post()
                .uri(GATEWAY_SESSIONS)
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestWith(clientId, clientSecret)))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error).then(Mono.error(unAuthorized())))
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error)
                        .then(Mono.error(networkServiceCallFailed())))
                .bodyToMono(Session.class)
                .doOnSubscribe(subscription -> logger.info("About to call gateway to get access token for HAS"));
    }

    private HASGatewayClient.SessionRequest requestWith(String clientId, String clientSecret) {
        return new HASGatewayClient.SessionRequest(clientId, clientSecret);
    }

    @AllArgsConstructor
    @Value
    private static class SessionRequest {
        String clientId;
        String clientSecret;
    }

}
