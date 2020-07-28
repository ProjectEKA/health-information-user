package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static reactor.core.publisher.Flux.fromIterable;

public class GrantedConsentTask extends ConsentTask {
    private final GatewayServiceClient gatewayClient;
    private final Gateway gateway;
    private final CacheAdapter<String, String> gatewayResponseCache;

    public GrantedConsentTask(ConsentRepository consentRepository,
                              GatewayServiceClient gatewayClient,
                              Gateway gateway,
                              CacheAdapter<String, String> gatewayResponseCache) {
        super(consentRepository);
        this.gatewayClient = gatewayClient;
        this.gateway = gateway;
        this.gatewayResponseCache = gatewayResponseCache;
    }

    private Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, String cmSuffix) {
        var requestId = UUID.randomUUID();
        return gatewayResponseCache.put(requestId.toString(), consentRequestId)
                .then(gateway.token())
                .flatMap(token -> {
                    var consentArtefactRequest = ConsentArtefactRequest
                            .builder()
                            .consentId(reference.getId())
                            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                            .requestId(requestId)
                            .build();
                    return gatewayClient.requestConsentArtefact(consentArtefactRequest, cmSuffix, token);
                });
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        return consentRepository.get(consentNotification.getConsentRequestId())
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .flatMap(consentRequest -> consentRepository.updateConsentRequestStatus(
                        ConsentStatus.GRANTED, consentNotification.getConsentRequestId()).thenReturn(consentRequest))
                .map(consentRequest -> getCmSuffix(consentRequest.getPatient().getId()))
                .flatMapMany(cmSuffix -> fromIterable(consentNotification.getConsentArtefacts())
                        .flatMap(reference -> perform(reference, consentNotification.getConsentRequestId(), cmSuffix)))
                .ignoreElements();
    }

    private String getCmSuffix(String patientId) {
        String[] parts = patientId.split("@");
        return parts[1];
    }
}
