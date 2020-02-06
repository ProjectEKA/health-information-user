package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
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
    private HiuProperties hiuProperties;

    public ConsentManagerClient(WebClient.Builder webClient,
                                ConsentManagerServiceProperties consentManagerServiceProperties,
                                HiuProperties hiuProperties) {
        this.webClient = webClient.baseUrl(consentManagerServiceProperties.getUrl()).build();
        this.hiuProperties = hiuProperties;
    }

    public Mono<ConsentCreationResponse> createConsentRequestInConsentManager(
            ConsentRequest consentRequest) {
        return webClient
                .post()
                .uri("/consent-requests")
                .header("Authorization",
                        TokenUtils.encodeHIUId(hiuProperties.getId()))
                .body(Mono.just(consentRequest),
                        ConsentRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(creationFailed()))
                .bodyToMono(ConsentCreationResponse.class);
    }

    public Mono<ConsentArtefactResponse> getConsentArtefact(String consentId) {
        return webClient
                .get()
                .uri(String.format("/consents/%s/", consentId))
                .header("Authorization",
                        TokenUtils.encodeHIUId(hiuProperties.getId()))
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(fetchConsentArtefactFailed()))
                .bodyToMono(ConsentArtefactResponse.class);
    }
}
