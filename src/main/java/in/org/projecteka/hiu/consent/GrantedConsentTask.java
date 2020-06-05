package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.Consent;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactRequest;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public class GrantedConsentTask implements ConsentTask {

    private GatewayServiceClient gatewayClient;
    private CentralRegistry centralRegistry;
    private ConsentRepository consentRepository;
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    private HiuProperties properties;

    public GrantedConsentTask(GatewayServiceClient gatewayServiceClient,
                              CentralRegistry centralRegistry,
                              ConsentRepository consentRepository,
                              DataFlowRequestPublisher dataFlowRequestPublisher,
                              HiuProperties properties) {

        this.gatewayClient = gatewayServiceClient;
        this.centralRegistry = centralRegistry;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.properties = properties;
    }

    @Override
    public Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        var requestId = UUID.randomUUID();
        return consentRepository.get(consentRequestId)
                .flatMap(consentRequest -> {
                    var cmSuffix = getCmSuffix(consentRequest.getPatient().getId());
                    return centralRegistry.token()
                            .flatMap(token -> gatewayClient
                                    .requestConsentArtefact(ConsentArtefactRequest
                                            .builder()
                                            .consentId(reference.getId())
                                            .timestamp(LocalDateTime.now())
                                            .requestId(requestId)
                                            .build(),cmSuffix, token))

//                            .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
//                                    consentArtefactResponse.getConsentDetail(),
//                                    consentArtefactResponse.getStatus(),
//                                    consentRequestId)
//                                    .then(Mono.defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
//                                            consentArtefactResponse.getConsentDetail().getConsentId(),
//                                            consentArtefactResponse.getConsentDetail().getPermission().getDateRange(),
//                                            consentArtefactResponse.getSignature(),
//                                            properties.getDataPushUrl()))))
                            .then();
                });
    }

    private String getCmSuffix(String patientId) {
        String[] parts = patientId.split("@");
        return parts[1];
    }
}
