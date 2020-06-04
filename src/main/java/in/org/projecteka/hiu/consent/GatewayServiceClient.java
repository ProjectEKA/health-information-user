package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.consent.model.ConsentFetchRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static in.org.projecteka.hiu.consent.ConsentException.fetchConsentArtefactFailed;
import static java.util.function.Predicate.not;

public class GatewayServiceClient {
    private static final String GATEWAY_PATH_CONSENT_REQUESTS_INIT = "/v1/consent-requests/init";
    private final WebClient webClient;
    private GatewayServiceProperties gatewayServiceProperties;
    private static final String GATEWAY_PATH_CONSENT_ARTEFACT_REQUESTS_FETCH ="v1/consent/fetch";

    public GatewayServiceClient(WebClient.Builder webClient,
                                GatewayServiceProperties gatewayServiceProperties) {
        this.webClient = webClient.baseUrl(gatewayServiceProperties.getBaseUrl()).build();
        this.gatewayServiceProperties = gatewayServiceProperties;
    }

    public Mono<Void> sendConsentRequest(String token, String cmSuffix, ConsentRequest request) {
        return webClient
                .post()
                .uri(GATEWAY_PATH_CONSENT_REQUESTS_INIT)
                .header("Authorization", token)
                .header("X-CM-ID", cmSuffix)
                .body(Mono.just(request),
                        ConsentRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(creationFailed()))
                .toBodilessEntity()
                .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                .then();
    }

    public Mono<Void> getConsentArtefactRequest(ConsentFetchRequest consentFetchRequest, String token) {
        return webClient
                .post()
                .uri(GATEWAY_PATH_CONSENT_ARTEFACT_REQUESTS_FETCH)
                .header("Authorization", token)
                .header("X-CM-ID", "ncg")
                .body(Mono.just(consentFetchRequest),
                        ConsentFetchRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(fetchConsentArtefactFailed()))
                .toBodilessEntity()
                .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                .then();
    }
}
