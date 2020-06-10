package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;

@AllArgsConstructor
public abstract class ConsentTask {
    protected final ConsentRepository consentRepository;

    abstract Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp);

    public Mono<Void> processNotificationRequest(String consentRequestId,
                                                 ConsentStatus status) {
        return consentRepository.getConsentRequestStatus(consentRequestId)
                .switchIfEmpty(Mono.error(ClientError.consentRequestNotFound()))
                .filter(consentStatus -> consentStatus == REQUESTED)
                .switchIfEmpty(Mono.error(ClientError.consentRequestAlreadyUpdated()))
                .flatMap(consentRequest -> consentRepository.updateConsentRequestStatus(status, consentRequestId));
    }
}
