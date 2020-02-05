package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.dataflow.Consent;
import in.org.projecteka.hiu.consent.model.consentmanager.dataflow.DataFlowRequestResponse;
import in.org.projecteka.hiu.consent.model.consentmanager.dataflow.HIDataRange;
import in.org.projecteka.hiu.consent.model.consentmanager.dataflow.Request;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ClientError.invalidConsentManager;

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

    public Mono<Void> handleNotification(String consentManagerId,
                                         ConsentNotificationRequest consentNotificationRequest) {
        return validateRequest(consentNotificationRequest.getConsentRequestId())
                .flatMap(consentRequest -> {
                    boolean validConsentManager = isValidConsentManager(consentManagerId, consentRequest);
                    if (validConsentManager) {
                        return fetchConsentArtefacts(consentNotificationRequest)
                                .flatMap(consentArtefactResponse ->
                                        consentRepository.insertConsentArtefact(consentArtefactResponse.getConsentDetail())
                                                .then(initiateDataFlowRequest(consentArtefactResponse))).then();
                    } else
                        return Mono.error(invalidConsentManager());
                });
    }

    private Mono<DataFlowRequestResponse> initiateDataFlowRequest(ConsentArtefactResponse consentArtefactResponse) {
        return consentManagerClient.initiateDataFlowRequest(Request.builder()
                .consent(Consent.builder().
                        id(consentArtefactResponse.getConsentDetail().getConsentId())
                        .digitalSignature(consentArtefactResponse.getSignature())
                        .build())
                .callBackUrl(hiuProperties.getCallBackUrl())
                .build());
    }

    private Flux<ConsentArtefactResponse> fetchConsentArtefacts(ConsentNotificationRequest consentNotificationRequest) {
        return Flux.fromIterable(consentNotificationRequest.getConsents())
                .flatMap(consentArtefactReference ->
                        consentManagerClient.getConsentArtefact(consentArtefactReference.getId()));
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> validateRequest(String consentRequestId) {
        return consentRepository.get(consentRequestId).switchIfEmpty(Mono.error(consentRequestNotFound()));
    }

    private boolean isValidConsentManager(String consentManagerId,
                                          in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest) {
        return consentRequest.getPatient().getId().contains(consentManagerId);
    }
}