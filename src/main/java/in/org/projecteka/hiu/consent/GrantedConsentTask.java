package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static in.org.projecteka.hiu.consent.CustomScheduler.scheduleThis;

@AllArgsConstructor
public class GrantedConsentTask implements ConsentTask {

    private GatewayServiceClient gatewayClient;
    private CentralRegistry centralRegistry;
    private ConsentRepository consentRepository;
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    private HiuProperties properties;
    private GatewayServiceProperties gatewayServiceProperties;
    private Cache<String, Optional<ConsentArtefactResponse>> gatewayResponseCache;


    @Override
    public Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        var requestId = UUID.randomUUID();
        return consentRepository.get(consentRequestId)
                .flatMap(consentRequest -> {
                    var cmSuffix = getCmSuffix(consentRequest.getPatient().getId());
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

                });
    }

    private String getCmSuffix(String patientId) {
        String[] parts = patientId.split("@");
        return parts[1];
    }
}
