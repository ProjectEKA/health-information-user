package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.*;

public class RevokedConsentTask extends ConsentTask {
    private HealthInformationPublisher healthInformationPublisher;

    public RevokedConsentTask(ConsentRepository consentRepository, HealthInformationPublisher healthInformationPublisher) {
        super(consentRepository);
        this.healthInformationPublisher = healthInformationPublisher;
    }


    private Mono<Void> processArtefactReference(ConsentArtefactReference reference, LocalDateTime timestamp) {
        return consentRepository.updateStatus(reference, REVOKED, timestamp)
                .then(healthInformationPublisher.publish(reference));
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        if (consentNotification.getConsentArtefacts().isEmpty()) {
            return processNotificationRequest(consentNotification.getConsentRequestId(), EXPIRED);
        }
        return validateConsents(consentNotification.getConsentArtefacts())
                .then(Mono.defer(() -> Flux.fromIterable(consentNotification.getConsentArtefacts())
                        .flatMap(reference -> processArtefactReference(reference, timeStamp))
                        .then()));
    }

    private Mono<List<ConsentArtefact>> validateConsents(List<ConsentArtefactReference> consentArtefacts) {
        return Flux.fromIterable(consentArtefacts)
                .flatMap(consentArtefact -> consentRepository.getConsent(consentArtefact.getId(), ConsentStatus.GRANTED)
                        .switchIfEmpty(Mono.error(consentArtefactNotFound())))
                .collectList();
    }
}
