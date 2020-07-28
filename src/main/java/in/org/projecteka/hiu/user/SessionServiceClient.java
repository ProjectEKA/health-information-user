package in.org.projecteka.hiu.user;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.common.Constants;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.common.Constants.AUTH_PASSWORD;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@AllArgsConstructor
public class SessionServiceClient {
    private final WebClient webClient;
    private static final String X_TOKEN_HEADER_NAME = "X-Token";

    public SessionServiceClient(WebClient.Builder webClient, String baseUrl) {
        this.webClient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Boolean> validateToken(String token) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder.path(AUTH_PASSWORD).build())
                .header(X_TOKEN_HEADER_NAME, token)
                .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .accept(APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(ClientError.unAuthorized()))
                .onStatus(HttpStatus::isError, clientResponse -> Mono.error(ClientError.networkServiceCallFailed()))
                .bodyToMono(Boolean.class);
    }
}
