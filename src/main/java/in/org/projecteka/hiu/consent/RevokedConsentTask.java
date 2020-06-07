package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.ErrorCode.VALIDATION_FAILED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.*;
import static org.springframework.http.HttpStatus.CONFLICT;

public class RevokedConsentTask implements ConsentTask {
    private ConsentRepository consentRepository;
    private HealthInformationPublisher healthInformationPublisher;

    public RevokedConsentTask(ConsentRepository consentRepository, HealthInformationPublisher healthInformationPublisher) {
        this.consentRepository = consentRepository;
        this.healthInformationPublisher = healthInformationPublisher;
    }


    private Mono<Void> processArtefactReference(ConsentArtefactReference reference, LocalDateTime timestamp) {
        return consentRepository.updateStatus(reference, REVOKED, timestamp)
                .then(healthInformationPublisher.publish(reference));
    }

    private Mono<Void> processNotificationRequest(String consentRequestId,
                                                  ConsentStatus status) {
        return consentRepository.getConsentRequestStatus(consentRequestId)
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .filter(consentStatus -> consentStatus == REQUESTED)
                .switchIfEmpty(Mono.error(new ClientError(CONFLICT,
                        new ErrorRepresentation(new Error(VALIDATION_FAILED,
                                "Consent request is already updated.")))))
                .flatMap(consentRequest -> consentRepository.updateConsentRequestStatus(status,consentRequestId));
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
