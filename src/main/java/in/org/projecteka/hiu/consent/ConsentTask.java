package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ConsentTask {
    Mono<Void> perform(ConsentNotification consentNotification,LocalDateTime timeStamp);
}
