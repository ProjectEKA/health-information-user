package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import static in.org.projecteka.hiu.consent.Transformer.toConsentManagerConsent;
import static in.org.projecteka.hiu.consent.Transformer.toConsentRequest;

import in.org.projecteka.hiu.consent.model.consentmanager.Consent;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import reactor.core.publisher.Mono;

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

    public Mono<ConsentCreationResponse> createConsentRequest(
            String requesterId,
            ConsentRequestDetails consentRequestDetails) {
        Consent consentWithHIURequesterInfo = toConsentManagerConsent(
                requesterId,
                consentRequestDetails.getConsent(),
                hiuProperties.getId(),
                hiuProperties.getName());
        return consentManagerClient.createConsentRequestInConsentManager(new ConsentRepresentation(consentWithHIURequesterInfo))
                .flatMap(consentCreationResponse -> consentRepository.insertToConsentRequest(toConsentRequest(
                        consentCreationResponse.getId(),
                        requesterId,
                        consentRequestDetails.getConsent()))
                        .then(Mono.just(ConsentCreationResponse.builder().id(consentCreationResponse.getId()).build())));
    }




}

