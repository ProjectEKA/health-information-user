package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ClientError.invalidConsentManager;

@AllArgsConstructor
public class ConsentService {
    private final ConsentManagerClient consentManagerClient;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;

    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestData consentRequestData) {
        var consentRequest = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName(),
                hiuProperties.getCallBackUrl());
        return consentManagerClient.createConsentRequest(new ConsentRequest(consentRequest))
                .flatMap(consentCreationResponse ->
                        consentRepository
                                .insert(consentRequestData.getConsent().toConsentRequest(
                                        consentCreationResponse.getId(),
                                        requesterId,
                                        hiuProperties.getCallBackUrl()))
                                .thenReturn(consentCreationResponse.getId()))
                .map(ConsentCreationResponse::new);
    }

    public Mono<Void> handleNotification(String consentManagerId,
                                         ConsentNotificationRequest consentNotificationRequest) {
        return validateRequest(consentNotificationRequest.getConsentRequestId())
                .flatMap(consentRequest -> isValidConsentManager(consentManagerId, consentRequest)
                        ? upsertConsentArtefacts(consentNotificationRequest).then()
                        : Mono.error(invalidConsentManager()));
    }

    private Flux<Void> upsertConsentArtefacts(ConsentNotificationRequest consentNotificationRequest) {
        return Flux.fromIterable(consentNotificationRequest.getConsents())
                .flatMap(consentArtefactReference ->
                        consentArtefactReference.getStatus() == ConsentStatus.GRANTED
                                ? insertConsentArtefact(consentArtefactReference, consentNotificationRequest.getConsentRequestId())
                                : updateConsentArtefactStatus(consentArtefactReference));
    }

    private Mono<Void> updateConsentArtefactStatus(ConsentArtefactReference consentArtefactReference) {
        return consentRepository.updateStatus(consentArtefactReference).then();
    }

    private Mono<Void> insertConsentArtefact(ConsentArtefactReference consentArtefactReference,
                                             String consentRequestId) {
        return consentManagerClient.getConsentArtefact(consentArtefactReference.getId())
                .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
                        consentArtefactResponse.getConsentDetail(),
                        consentArtefactResponse.getStatus(),
                        consentRequestId)
                        .then(dataFlowRequestPublisher.broadcastDataFlowRequest(
                                consentArtefactResponse.getConsentDetail().getConsentId(),
                                consentArtefactResponse.getSignature(),
                                hiuProperties.getCallBackUrl())))
                .then();
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> validateRequest(String consentRequestId) {
        return consentRepository.get(consentRequestId).switchIfEmpty(Mono.error(consentRequestNotFound()));
    }

    private boolean isValidConsentManager(String consentManagerId,
                                          in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest) {
        return consentRequest.getPatient().getId().contains(consentManagerId);
    }
}