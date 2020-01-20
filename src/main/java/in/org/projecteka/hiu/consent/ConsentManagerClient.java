package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.consent.ConsentException.creationFailed;
import static java.util.function.Predicate.not;

public class ConsentManagerClient {
    private final WebClient webClientBuilder;
    private HiuProperties hiuProperties;

    public ConsentManagerClient(WebClient.Builder webClientBuilder,
                                ConsentManagerServiceProperties consentManagerServiceProperties,
                                HiuProperties hiuProperties) {
        this.webClientBuilder =
                webClientBuilder.baseUrl(consentManagerServiceProperties.getUrl()).build();
        this.hiuProperties = hiuProperties;
    }

    public Mono<ConsentCreationResponse> createConsentRequestInConsentManager(
            ConsentRepresentation consentRepresentation) {
        return webClientBuilder
                .post()
                .uri("/consent-requests")
                .header("Authorization",
                        TokenUtils.encodeHIUId(hiuProperties.getId()))
                .body(Mono.just(consentRepresentation),
                        ConsentRepresentation.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> {
                            System.out.println("Print ==================================");
                            System.out.println(clientResponse);
                            System.out.println("Print ==================================");
                            return Mono.error(creationFailed());
                        })
                .bodyToMono(ConsentCreationResponse.class);
    }
}
