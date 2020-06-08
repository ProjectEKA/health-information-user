package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.common.DelayTimeoutException;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static in.org.projecteka.hiu.common.CustomScheduler.scheduleThis;


public class GrantedConsentTask extends ConsentTask {
    private GatewayServiceClient gatewayClient;
    private CentralRegistry centralRegistry;
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    private HiuProperties properties;
    private GatewayServiceProperties gatewayServiceProperties;
    private Cache<String, Optional<ConsentArtefactResponse>> gatewayResponseCache;

    public GrantedConsentTask(ConsentRepository consentRepository,
                              GatewayServiceClient gatewayClient,
                              CentralRegistry centralRegistry,
                              DataFlowRequestPublisher dataFlowRequestPublisher,
                              HiuProperties properties,
                              GatewayServiceProperties gatewayServiceProperties,
                              Cache<String, Optional<ConsentArtefactResponse>> gatewayResponseCache) {
        super(consentRepository);
        this.gatewayClient = gatewayClient;
        this.centralRegistry = centralRegistry;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.properties = properties;
        this.gatewayServiceProperties = gatewayServiceProperties;
        this.gatewayResponseCache = gatewayResponseCache;
    }

    private Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, String cmSuffix) {
        var requestId = UUID.randomUUID();
        return centralRegistry.token()
                .flatMap(token -> {
                    var consentArtefactRequest = ConsentArtefactRequest
                            .builder()
                            .consentId(reference.getId())
                            .timestamp(LocalDateTime.now())
                            .requestId(requestId)
                            .build();

                    return scheduleThis(gatewayClient
                            .requestConsentArtefact(consentArtefactRequest, cmSuffix, token))
                            .timeout(Duration.ofMillis(gatewayServiceProperties.getRequestTimeout()))
                            .responseFrom(discard ->
                                    Mono.defer(() -> gatewayResponseCache.asMap()
                                            .getOrDefault(requestId.toString(), Optional.empty())
                                            .map(Mono::just)
                                            .orElse(Mono.empty())));
                })
                .onErrorResume(DelayTimeoutException.class, (e) -> Mono.error(ClientError.invalidDataFromGateway()))
                .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
                        consentArtefactResponse.getConsentDetail(),
                        consentArtefactResponse.getStatus(),
                        consentRequestId)
                        .then(Mono.defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                                consentArtefactResponse.getConsentDetail().getConsentId(),
                                consentArtefactResponse.getConsentDetail().getPermission().getDateRange(),
                                consentArtefactResponse.getSignature(),
                                properties.getDataPushUrl()))));
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        return consentRepository.get(consentNotification.getConsentRequestId())
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .flatMap(consentRequest -> {
                    var cmSuffix = getCmSuffix(consentRequest.getPatient().getId());
                    return Flux.fromIterable(consentNotification.getConsentArtefacts())
                            .flatMap(reference -> perform(reference, consentRequest.getId(), cmSuffix))
                            .then();
                });
    }

    private String getCmSuffix(String patientId) {
        String[] parts = patientId.split("@");
        return parts[1];
    }
}
