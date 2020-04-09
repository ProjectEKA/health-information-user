package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
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

import java.util.Date;
import java.util.List;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ClientError.validationFailed;
import static in.org.projecteka.hiu.ErrorCode.VALIDATION_FAILED;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static org.springframework.http.HttpStatus.CONFLICT;

@AllArgsConstructor
public class ConsentService {
    private final ConsentManagerClient consentManagerClient;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final PatientService patientService;
    private final CentralRegistry centralRegistry;
    private final HealthInformationPublisher healthInformationPublisher;

    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestData consentRequestData) {
        var consentRequest = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName(),
                hiuProperties.getConsentNotificationUrl());
        return centralRegistry.token()
                .flatMap(token -> consentManagerClient.createConsentRequest(new ConsentRequest(consentRequest), token))
                .flatMap(consentCreationResponse ->
                        consentRepository
                                .insert(consentRequestData.getConsent().toConsentRequest(
                                        consentCreationResponse.getId(),
                                        requesterId,
                                        hiuProperties.getConsentNotificationUrl()))
                                .then(Mono.fromCallable(consentCreationResponse::getId)))
                .map(ConsentCreationResponse::new);
    }

    public Mono<Void> handleNotification(ConsentNotificationRequest consentNotificationRequest) {
        if (consentNotificationRequest.getStatus() == ConsentStatus.GRANTED) {
            // TODO: Need to figure out how we are going to figure out consent manager id.
            // most probably need to have a mapping of @ncg = consent manager id
            return validateRequest(consentNotificationRequest.getConsentRequestId())
                    .flatMap(consentRequest -> upsertConsentArtefacts(consentNotificationRequest).then());
        } else if (consentNotificationRequest.getStatus().equals(ConsentStatus.REVOKED)) {
            return validateConsents(consentNotificationRequest.getConsentArtefacts())
                    .flatMap(consentArtefacts -> upsertConsentArtefacts(consentNotificationRequest).then());
        } else if (DENIED == consentNotificationRequest.getStatus()) {
            return validateRequest(consentNotificationRequest.getConsentRequestId())
                    .filter(consentRequest -> consentRequest.getStatus() == REQUESTED)
                    .switchIfEmpty(Mono.error(new ClientError(CONFLICT,
                            new ErrorRepresentation(new Error(VALIDATION_FAILED,
                                    "Consent request is already updated.")))))
                    .flatMap(consentRequest ->
                            consentRepository.updateConsent(consentRequest.getId(),
                                    consentRequest.toBuilder().status(DENIED).build()));
        }
        //TODO: Need to validate for all scenarios
        return Mono.error(validationFailed());
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
                .switchIfEmpty(Mono.just(consentRequest.getStatus()))
                .map(status -> consentRequest.toBuilder().status(status).build());
    }

    private Flux<Void> upsertConsentArtefacts(ConsentNotificationRequest consentNotificationRequest) {
        return Flux.fromIterable(consentNotificationRequest.getConsentArtefacts())
                .flatMap(consentArtefactReference -> {
                    if (consentNotificationRequest.getStatus().equals(ConsentStatus.GRANTED))
                        return processGrantedConsent(consentArtefactReference,
                                consentNotificationRequest.getConsentRequestId());
                    else if (consentNotificationRequest.getStatus().equals(ConsentStatus.REVOKED))
                        return processRevokedConsent(consentArtefactReference,
                                consentNotificationRequest.getStatus(),
                                consentNotificationRequest.getTimestamp());
                    else
                        return processRejectedConsent(consentArtefactReference,
                                consentNotificationRequest.getStatus(),
                                consentNotificationRequest.getTimestamp());
                });
    }

    private Mono<Void> processRevokedConsent(ConsentArtefactReference consentArtefactReference,
                                             ConsentStatus status,
                                             Date timestamp) {
        return consentRepository.updateStatus(consentArtefactReference, status, timestamp)
                .then(healthInformationPublisher.publish(consentArtefactReference));
    }

    private Mono<Void> processRejectedConsent(ConsentArtefactReference consentArtefactReference,
                                              ConsentStatus status,
                                              Date timestamp) {
        return consentRepository.updateStatus(consentArtefactReference, status, timestamp).then();
    }

    private Mono<Void> processGrantedConsent(ConsentArtefactReference consentArtefactReference,
                                             String consentRequestId) {
        return centralRegistry.token()
                .flatMap(token -> consentManagerClient.getConsentArtefact(consentArtefactReference.getId(), token))
                .flatMap(consentArtefactResponse -> consentRepository.insertConsentArtefact(
                        consentArtefactResponse.getConsentDetail(),
                        consentArtefactResponse.getStatus(),
                        consentRequestId)
                        .then(Mono.defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                                consentArtefactResponse.getConsentDetail().getConsentId(),
                                consentArtefactResponse.getConsentDetail().getPermission().getDateRange(),
                                consentArtefactResponse.getSignature(),
                                hiuProperties.getDataPushUrl()))))
                .then();
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> validateRequest(String consentRequestId) {
        return consentRepository.get(consentRequestId).switchIfEmpty(Mono.error(consentRequestNotFound()));
    }

    private Mono<List<ConsentArtefact>> validateConsents(List<ConsentArtefactReference> consentArtefacts) {
        return Flux.fromIterable(consentArtefacts)
                .flatMap(consentArtifact -> consentRepository.getConsent(consentArtifact.getId(), ConsentStatus.GRANTED)
                        .switchIfEmpty(Mono.error(consentArtefactNotFound())))
                .collectList();
    }
}