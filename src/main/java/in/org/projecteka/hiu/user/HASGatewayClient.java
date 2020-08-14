package in.org.projecteka.hiu.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.networkServiceCallFailed;
import static in.org.projecteka.hiu.ClientError.unAuthorized;
import static in.org.projecteka.hiu.common.Constants.HAS_AUTH_ACCESS_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.BodyInserters.fromFormData;

public class HASGatewayClient {
    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(HASGatewayClient.class);

    public HASGatewayClient(WebClient.Builder webClient, String baseUrl) {
        this.webClient = webClient.baseUrl(baseUrl).build();
    }

    public Mono<Session> getToken(String url, MultiValueMap<String, String> formData) {
        return webClient
                .post()
                .uri(url + HAS_AUTH_ACCESS_TOKEN)
                .contentType(APPLICATION_FORM_URLENCODED)
                .accept(APPLICATION_JSON)
                .body(fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error).then(Mono.error(unAuthorized())))
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(logger::error)
                        .then(Mono.error(networkServiceCallFailed())))
                .bodyToMono(Session.class)
                .doOnSubscribe(subscription -> logger.info("About to call HAS for getting access token"));
    }

}
