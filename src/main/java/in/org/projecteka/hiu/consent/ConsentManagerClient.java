package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static in.org.projecteka.hiu.consent.ConsentException.fetchConsentArtefactFailed;
import static java.util.function.Predicate.not;

public class ConsentManagerClient {
    private final WebClient webClient;

    public ConsentManagerClient(WebClient.Builder webClient,
                                ConsentManagerServiceProperties consentManagerServiceProperties) {
        this.webClient = webClient.baseUrl(consentManagerServiceProperties.getUrl()).build();
    }

    public Mono<ConsentCreationResponse> createConsentRequest(
            ConsentRequest consentRequest,
            String token) {
        return webClient
                .post()
                .uri("/consent-requests")
                .header("Authorization", token)
                .body(Mono.just(consentRequest),
                        ConsentRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(creationFailed()))
                .bodyToMono(ConsentCreationResponse.class);
    }

    public Mono<ConsentArtefactResponse> getConsentArtefact(String consentId, String token) {
        return webClient
                .get()
                .uri(String.format("/consents/%s/", consentId))
                .header("Authorization", token)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(fetchConsentArtefactFailed()))
                .bodyToMono(ConsentArtefactResponse.class);
    }
}
