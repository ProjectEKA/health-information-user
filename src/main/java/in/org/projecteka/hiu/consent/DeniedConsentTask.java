package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentNotification;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;

public class DeniedConsentTask extends ConsentTask {

    public DeniedConsentTask(ConsentRepository consentRepository) {
        super(consentRepository);
    }

    @Override
    public Mono<Void> perform(ConsentNotification consentNotification, LocalDateTime timeStamp) {
        return super.processNotificationRequest(consentNotification.getConsentRequestId(), DENIED);
    }
}
