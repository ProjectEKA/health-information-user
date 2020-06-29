package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;


public class GrantedConsentTask extends ConsentTask {
    private final GatewayServiceClient gatewayClient;
    private final Gateway gateway;
    private final Cache<String, String> gatewayResponseCache;

    public GrantedConsentTask(ConsentRepository consentRepository,
                              GatewayServiceClient gatewayClient,
                              Gateway gateway,
                              Cache<String, String> gatewayResponseCache) {
        super(consentRepository);
        this.gatewayClient = gatewayClient;
        this.gateway = gateway;
        this.gatewayResponseCache = gatewayResponseCache;
    }

    private Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, String cmSuffix) {
        var requestId = UUID.randomUUID();
        gatewayResponseCache.put(requestId.toString(), consentRequestId);
        return gateway.token()
                .flatMap(token -> {
                    var consentArtefactRequest = ConsentArtefactRequest
                            .builder()
                            .consentId(reference.getId())
                            .timestamp(LocalDateTime.now())
                            .requestId(requestId)
                            .build();
                    return gatewayClient.requestConsentArtefact(consentArtefactRequest, cmSuffix, token);
                });
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        return consentRepository.get(consentNotification.getConsentRequestId())
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .flatMap(consentRequest -> {
                    var cmSuffix = getCmSuffix(consentRequest.getPatient().getId());
                    return Flux.fromIterable(consentNotification.getConsentArtefacts())
                            .flatMap(reference -> perform(reference, consentNotification.getConsentRequestId(),
                                    cmSuffix))
                            .then();
                });
    }

    private String getCmSuffix(String patientId) {
        String[] parts = patientId.split("@");
        return parts[1];
    }
}
