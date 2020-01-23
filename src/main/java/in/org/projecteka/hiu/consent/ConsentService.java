package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
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

    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestData consentRequestData) {
        var consentRequest = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName(),
                hiuProperties.getCallBackUrl());
        return consentManagerClient.createConsentRequestInConsentManager(
                new ConsentRequest(consentRequest))
                .flatMap(consentCreationResponse ->
                        consentRepository.insert(consentRequestData.getConsent().toConsentRequest(
                                consentCreationResponse.getId(),
                                requesterId,
                                hiuProperties.getCallBackUrl()))
                                .thenReturn(ConsentCreationResponse.builder().id(consentCreationResponse.getId()).build()));
    }
}