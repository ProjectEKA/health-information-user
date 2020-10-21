package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentOnNotifyRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.model.FindPatientRequest;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static in.org.projecteka.hiu.clients.PatientSearchThrowable.notFound;
import static in.org.projecteka.hiu.clients.PatientSearchThrowable.unknown;
import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;
import static in.org.projecteka.hiu.common.Constants.X_CM_ID;
import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static java.time.Duration.ofMillis;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class GatewayServiceClient {
    private static final String GATEWAY_PATH_CONSENT_REQUESTS_INIT = "/consent-requests/init";
    private static final String GATEWAY_PATH_CONSENT_ARTEFACT_FETCH = "/consents/fetch";
    private static final String GATEWAY_PATH_CONSENT_ON_NOTIFY = "/consents/hiu/on-notify";

    private final WebClient webClient;
    private final GatewayProperties gatewayProperties;
    private final Gateway gateway;
    private static final Logger logger = getLogger(GatewayServiceClient.class);

    /*
    * {
      "requestId": "5f7a535d-a3fd-416b-b069-c97d021fbacd",
      "timestamp": "2020-10-21T11:39:45.635Z",
      "acknowledgement": [
        {
          "status": "OK",
          "consentId": "<consent-artefact-id>"
        }
      ],
      "error": {
        "code": 1000,
        "message": "string"
      },
      "resp": {
        "requestId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
      }
    }*/
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
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .body(just(request), ConsentRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> clientResponse.bodyToMono(Properties.class)
                                        .doOnNext(properties -> logger.error(properties.toString()))
                                        .then(error(creationFailed())))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Boolean> findPatientWith(FindPatientRequest request, String cmSuffix) {
        return gateway.token()
                .flatMap(token -> webClient.
                        post()
                        .uri("/patients/find")
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .body(just(request), FindPatientRequest.class)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus == NOT_FOUND, clientResponse -> error(notFound()))
                        .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> error(unknown()))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout()))
                        .thenReturn(Boolean.TRUE));
    }

    public Mono<Void> requestConsentArtefact(ConsentArtefactRequest request, String cmSuffix) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_ARTEFACT_FETCH)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .body(just(request), ConsentArtefactRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> error(creationFailed()))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }

    public Mono<Void> sendConsentOnNotify(String cmSuffix, ConsentOnNotifyRequest request) {
        return gateway.token()
                .flatMap(token -> webClient
                        .post()
                        .uri(GATEWAY_PATH_CONSENT_ON_NOTIFY)
                        .header(AUTHORIZATION, token)
                        .header(X_CM_ID, cmSuffix)
                        .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                        .body(just(request), ConsentOnNotifyRequest.class)
                        .retrieve()
                        .onStatus(not(HttpStatus::is2xxSuccessful),
                                clientResponse -> clientResponse.bodyToMono(Properties.class)
                                        .doOnNext(properties -> logger.error(properties.toString()))
                                        .then(error(creationFailed())))
                        .toBodilessEntity()
                        .timeout(ofMillis(gatewayProperties.getRequestTimeout())))
                .then();
    }
}
