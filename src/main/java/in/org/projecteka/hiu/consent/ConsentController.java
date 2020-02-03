package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
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
        return consentService.fetchConsentArtefact(requesterId, consentNotificationRequest);
    }
}

