package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public class GrantedConsentTask implements ConsentTask {

    private ConsentManagerClient cmClient;
    private CentralRegistry centralRegistry;
    private ConsentRepository consentRepository;
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    private HiuProperties properties;

    public GrantedConsentTask(ConsentManagerClient cmClient,
                              CentralRegistry centralRegistry,
                              ConsentRepository consentRepository,
                              DataFlowRequestPublisher dataFlowRequestPublisher,
                              HiuProperties properties) {

        this.cmClient = cmClient;
        this.centralRegistry = centralRegistry;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.properties = properties;
    }

    @Override
    public Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        return centralRegistry.token()
                .flatMap(token -> cmClient.getConsentArtefact(reference.getId(), token))
                .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
                        consentArtefactResponse.getConsentDetail(),
                        consentArtefactResponse.getStatus(),
                        consentRequestId)
                        .then(Mono.defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                                consentArtefactResponse.getConsentDetail().getConsentId(),
                                consentArtefactResponse.getConsentDetail().getPermission().getDateRange(),
                                consentArtefactResponse.getSignature(),
                                properties.getDataPushUrl()))))
                .then();

    }
}
