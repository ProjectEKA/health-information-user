package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.consent.model.ConsentStatus.REVOKED;

public class RevokedConsentTask implements ConsentTask {
    private ConsentRepository consentRepository;
    private HealthInformationPublisher healthInformationPublisher;

    public RevokedConsentTask(ConsentRepository consentRepository, HealthInformationPublisher healthInformationPublisher) {
        this.consentRepository = consentRepository;
        this.healthInformationPublisher = healthInformationPublisher;
    }

    @Override
    public Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp) {
        return consentRepository.updateStatus(reference, REVOKED, timestamp)
                .then(healthInformationPublisher.publish(reference));
    }
}
