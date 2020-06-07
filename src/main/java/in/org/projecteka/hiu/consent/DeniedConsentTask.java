package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.ErrorCode.VALIDATION_FAILED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static org.springframework.http.HttpStatus.CONFLICT;

public class DeniedConsentTask implements ConsentTask {
    private ConsentRepository consentRepository;

    public DeniedConsentTask(ConsentRepository consentRepository) {
        this.consentRepository = consentRepository;
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        return consentRepository.getConsentRequestStatus(consentNotification.getConsentRequestId())
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .filter(consentStatus -> consentStatus == REQUESTED)
                .switchIfEmpty(Mono.error(new ClientError(CONFLICT,
                        new ErrorRepresentation(new Error(VALIDATION_FAILED,
                                "Consent request is already updated.")))))
                .flatMap(consentRequest -> consentRepository.updateConsentRequestStatus(DENIED, consentNotification.getConsentRequestId()));

    }
}
