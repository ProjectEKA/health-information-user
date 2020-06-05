package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ConsentTask {
    Mono<Void> perform(ConsentArtefactReference reference, String consentRequestId, LocalDateTime timestamp);
}
