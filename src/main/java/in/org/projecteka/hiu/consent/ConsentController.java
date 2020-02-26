package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ConsentController {
    private ConsentService consentService;

    @PostMapping("/consent-requests")
    public Mono<ConsentCreationResponse> createConsentRequest(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestBody ConsentRequestData consentRequestData) {
        String requesterId = TokenUtils.decode(authorization);
        return consentService.create(requesterId, consentRequestData);
    }

    @PostMapping("/consent/notification")
    public Mono<Void> consentNotification(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestBody ConsentNotificationRequest consentNotificationRequest) {
        String requesterId = TokenUtils.decode(authorization);
        return consentService.handleNotification(requesterId, consentNotificationRequest);
    }

    @GetMapping("/consent-requests")
    public Flux<ConsentRequestRepresentation> consentsFor(@RequestHeader(value = "Authorization") String authorization) {
        return consentService.requestsFrom(TokenUtils.decode(authorization));
    }
}

