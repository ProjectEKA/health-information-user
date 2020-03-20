package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.PatientService;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;

@AllArgsConstructor
public class ConsentService {
    private final ConsentManagerClient consentManagerClient;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final PatientService patientService;
    private final CentralRegistry centralRegistry;

    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestData consentRequestData) {
        var consentRequest = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName(),
                hiuProperties.getCallBackUrl());
        return centralRegistry.token()
                .flatMap(token -> consentManagerClient.createConsentRequest(new ConsentRequest(consentRequest), token))
                .flatMap(consentCreationResponse ->
                        consentRepository
                                .insert(consentRequestData.getConsent().toConsentRequest(
                                        consentCreationResponse.getId(),
                                        requesterId,
                                        hiuProperties.getCallBackUrl()))
                                .thenReturn(consentCreationResponse.getId()))
                .map(ConsentCreationResponse::new);
    }

    public Mono<Void> handleNotification(ConsentNotificationRequest consentNotificationRequest) {
        // TODO: Need to figure out how we are going to figure out consent manager id.
        // most probably need to have a mapping of @ncg = consent manager id
        return validateRequest(consentNotificationRequest.getConsentRequestId())
                .flatMap(consentRequest -> upsertConsentArtefacts(consentNotificationRequest).then());
    }

    public Flux<ConsentRequestRepresentation> requestsFrom(String requesterId) {
        return consentRepository.requestsFrom(requesterId)
                .flatMap(consentRequest ->
                        Mono.zip(patientService.patientWith(consentRequest.getPatient().getId()),
                                mergeArtefactWith(consentRequest)))
                .map(patientConsentRequest ->
                        toConsentRequestRepresentation(patientConsentRequest.getT1(), patientConsentRequest.getT2()));
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> mergeArtefactWith(
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest) {
        return consentRepository.getConsentDetails(consentRequest.getId())
                .take(1)
                .next()
                .map(map -> ConsentStatus.valueOf(map.get("status")))
                .switchIfEmpty(Mono.just(ConsentStatus.REQUESTED))
                .map(status -> consentRequest.toBuilder().status(status).build());
    }

    private Flux<Void> upsertConsentArtefacts(ConsentNotificationRequest consentNotificationRequest) {
        return Flux.fromIterable(consentNotificationRequest.getConsents())
                .flatMap(consentArtefactReference ->
                        consentArtefactReference.getStatus() == ConsentStatus.GRANTED
                        ? processGrantedConsent(consentArtefactReference,
                                consentNotificationRequest.getConsentRequestId())
                        : processRejectedConsent(consentArtefactReference));
    }

    private Mono<Void> processRejectedConsent(ConsentArtefactReference consentArtefactReference) {
        return consentRepository.updateStatus(consentArtefactReference).then();
    }

    private Mono<Void> processGrantedConsent(ConsentArtefactReference consentArtefactReference,
                                             String consentRequestId) {
        return centralRegistry.token()
                .flatMap(token -> consentManagerClient.getConsentArtefact(consentArtefactReference.getId(), token))
                .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
                        consentArtefactResponse.getConsentDetail(),
                        consentArtefactResponse.getStatus(),
                        consentRequestId)
                        .then(dataFlowRequestPublisher.broadcastDataFlowRequest(
                                consentArtefactResponse.getConsentDetail().getConsentId(),
                                consentArtefactResponse.getConsentDetail().getPermission().getDateRange(),
                                consentArtefactResponse.getSignature(),
                                hiuProperties.getCallBackUrl())))
                .then();
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> validateRequest(String consentRequestId) {
        return consentRepository.get(consentRequestId).switchIfEmpty(Mono.error(consentRequestNotFound()));
    }
}