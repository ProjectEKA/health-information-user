package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static in.org.projecteka.hiu.clients.PatientSearchThrowable.notFound;
import static in.org.projecteka.hiu.clients.PatientSearchThrowable.unknown;
import static in.org.projecteka.hiu.common.Constants.X_CM_ID;
import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

public class GatewayServiceClient {
    private static final String GATEWAY_PATH_CONSENT_REQUESTS_INIT = "/consent-requests/init";
    private static final String GATEWAY_PATH_CONSENT_ARTEFACT_FETCH = "/consents/fetch";

    private final WebClient webClient;
    private final GatewayProperties gatewayProperties;
    private final Gateway gateway;

    public GatewayServiceClient(WebClient.Builder webClient,
                                GatewayProperties gatewayProperties,
                                Gateway gateway) {
        this.webClient = webClient.baseUrl(gatewayProperties.getBaseUrl()).build();
        this.gatewayProperties = gatewayProperties;
        this.gateway = gateway;
    }

    public Mono<Void> sendConsentRequest(String cmSuffix, ConsentRequest request) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_REQUESTS_INIT)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .body(Mono.just(request),
                                ConsentRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> Mono.error(creationFailed()))
                        .toBodilessEntity()
                        .timeout(Duration.ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Boolean> findPatientWith(FindPatientRequest request, String cmSuffix) {
        return gateway.token()
                .flatMap(token -> webClient.
                        post()
                        .uri("/patients/find")
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .body(Mono.just(request),
                                FindPatientRequest.class)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND,
                                clientResponse -> Mono.error(notFound()))
                        .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(unknown()))
                        .toBodilessEntity()
                        .timeout(Duration.ofMillis(gatewayProperties.getRequestTimeout()))
                        .thenReturn(Boolean.TRUE));
    }

    public Mono<Void> requestConsentArtefact(ConsentArtefactRequest request, String cmSuffix) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_ARTEFACT_FETCH)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .body(Mono.just(request),
                                ConsentArtefactRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> Mono.error(creationFailed()))
                        .toBodilessEntity()
                        .timeout(Duration.ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }
}
