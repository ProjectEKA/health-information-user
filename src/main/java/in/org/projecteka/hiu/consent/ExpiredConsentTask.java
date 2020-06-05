package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public class ExpiredConsentTask implements ConsentTask {
    private ConsentRepository consentRepository;
    private DataFlowDeletePublisher dataFlowDeletePublisher;

    public ExpiredConsentTask(ConsentRepository consentRepository, DataFlowDeletePublisher dataFlowDeletePublisher) {
        this.consentRepository = consentRepository;
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
    }

    @Override
    public Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        return consentRepository.updateStatus(reference, ConsentStatus.EXPIRED, timestamp)
                .then(dataFlowDeletePublisher.broadcastConsentExpiry(reference.getId(),
                        consentRequestId));
    }
}
