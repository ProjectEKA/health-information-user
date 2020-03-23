package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ConsentController {
    private ConsentService consentService;

    @PostMapping("/consent-requests")
    public Mono<ConsentCreationResponse> createConsentRequest(@RequestBody ConsentRequestData consentRequestData) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUserName)
                .flatMap(requesterId -> consentService.create(requesterId, consentRequestData));
    }

    @PostMapping("/consent/notification")
    public Mono<Void> consentNotification(@RequestBody ConsentNotificationRequest consentNotificationRequest) {
        return consentService.handleNotification(consentNotificationRequest);
    }

    @GetMapping("/consent-requests")
    public Flux<ConsentRequestRepresentation> consentsFor() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUserName)
                .flatMapMany(username -> consentService.requestsFrom(username));
    }
}
