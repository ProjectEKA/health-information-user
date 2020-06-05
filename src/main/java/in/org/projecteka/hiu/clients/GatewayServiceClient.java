package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static in.org.projecteka.hiu.clients.PatientSearchException.notFound;
import static in.org.projecteka.hiu.clients.PatientSearchException.unknown;
import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static java.util.function.Predicate.not;

public class GatewayServiceClient {
    private static final String GATEWAY_PATH_CONSENT_REQUESTS_INIT = "/consent-requests/init";
    private static final String GATEWAY_PATH_CONSENT_ARTEFACT_FETCH = "/consents/fetch";
    private final WebClient webClient;
    private final GatewayServiceProperties gatewayServiceProperties;

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

    public Mono<Boolean> findPatientWith(FindPatientRequest request, String token, String cmSuffix) {
        return webClient.
                post()
                .uri("/patients/find")
                .header(HttpHeaders.AUTHORIZATION, token)
                .header("X-CM-ID", cmSuffix)
                .body(Mono.just(request),
                        FindPatientRequest.class)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND,
                        clientResponse -> Mono.error(notFound()))
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(unknown()))
                .toBodilessEntity()
                .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                .thenReturn(Boolean.TRUE);
    }

    public Mono<Void> requestConsentArtefact(ConsentArtefactRequest request, String cmSuffix, String token) {
        return webClient
                .post()
                .uri(GATEWAY_PATH_CONSENT_ARTEFACT_FETCH)
                .header("Authorization", token)
                .header("X-CM-ID", cmSuffix)
                .body(Mono.just(request),
                        ConsentArtefactRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(creationFailed()))
                .toBodilessEntity()
                .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                .then();
    }
}
