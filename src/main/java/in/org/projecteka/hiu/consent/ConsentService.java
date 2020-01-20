package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.consent.Transformer.toConsentRequest;

public class ConsentService {
    private final ConsentManagerClient consentManagerClient;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;

    public ConsentService(ConsentManagerClient consentManagerClient,
                          HiuProperties hiuProperties,
                          ConsentRepository consentRepository) {
        this.consentManagerClient = consentManagerClient;
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
    }

    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestDetails consentRequestDetails) {
        var consentRequest = consentRequestDetails.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName());
        return consentManagerClient.createConsentRequestInConsentManager(
                new ConsentRepresentation(consentRequest))
                .flatMap(consentCreationResponse ->
                        consentRepository.insert(toConsentRequest(
                                consentCreationResponse.getId(),
                                requesterId,
                                consentRequestDetails.getConsent()))
                                .thenReturn(ConsentCreationResponse.builder().id(consentCreationResponse.getId()).build()));
    }
}