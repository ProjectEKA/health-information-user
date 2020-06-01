package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.Consent;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestInitResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.PatientService;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ClientError.validationFailed;
import static in.org.projecteka.hiu.ErrorCode.INVALID_PURPOSE_OF_USE;
import static in.org.projecteka.hiu.ErrorCode.VALIDATION_FAILED;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@AllArgsConstructor
public class ConsentService {
    private final ConsentManagerClient consentManagerClient;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final DataFlowDeletePublisher dataFlowDeletePublisher;
    private final PatientService patientService;
    private final CentralRegistry centralRegistry;
    private final HealthInformationPublisher healthInformationPublisher;
    private final ConceptValidator conceptValidator;
    private final GatewayServiceClient gatewayServiceClient;

    /**
     * To be replaced by {@link #createRequest(String, ConsentRequestData) }
     * @param requesterId
     * @param consentRequestData
     * @return
     */
    @Deprecated
    public Mono<ConsentCreationResponse> create(String requesterId, ConsentRequestData consentRequestData) {
        return validateConsentRequest(consentRequestData)
                .then(createAndSaveConsent(requesterId, consentRequestData));
    }

    /**
     * To be replaced by {@link #sendConsentRequestToGateway}
     * @param requesterId
     * @param consentRequestData
     * @return
     */
    @Deprecated
    private Mono<ConsentCreationResponse> createAndSaveConsent(String requesterId,
                                                               ConsentRequestData consentRequestData) {
        var consentRequest = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                hiuProperties.getName(),
                hiuProperties.getConsentNotificationUrl(),
                conceptValidator);
        return centralRegistry.token()
                .flatMap(token -> consentManagerClient.createConsentRequest(
                        ConsentRequest.builder()
                                .requestId(UUID.randomUUID())
                                .consent(consentRequest)
                                .build(), token))
                .flatMap(consentCreationResponse ->
                        consentRepository
                                .insert(consentRequestData.getConsent().toConsentRequest(
                                        consentCreationResponse.getId(),
                                        requesterId,
                                        hiuProperties.getConsentNotificationUrl()))
                                .then(Mono.fromCallable(consentCreationResponse::getId)))
                .map(ConsentCreationResponse::new);
    }

    private Mono<Void> validateConsentRequest(ConsentRequestData consentRequestData) {
        return conceptValidator.validatePurpose(consentRequestData.getConsent().getPurpose().getCode())
                .flatMap(result -> result.booleanValue()
                        ? Mono.empty()
                        : Mono.error(new ClientError(BAD_REQUEST,
                        new ErrorRepresentation(new Error(INVALID_PURPOSE_OF_USE,
                                "Invalid Purpose Of Use")))));
    }

    public Mono<Void> handleNotification(ConsentNotificationRequest consentNotificationRequest) {
        switch (consentNotificationRequest.getStatus()) {
            case GRANTED:
                // TODO: Need to figure out how we are going to figure out consent manager id.
                // most probably need to have a mapping of @ncg = consent manager id
                return validateRequest(consentNotificationRequest.getConsentRequestId())
                        .flatMap(consentRequest -> upsertConsentArtefacts(consentNotificationRequest).then());
            case REVOKED:
            case EXPIRED:
                if (consentNotificationRequest.getConsentArtefacts().isEmpty()) {
                    return processNotificationRequest(consentNotificationRequest, EXPIRED);
                }
                return validateConsents(consentNotificationRequest.getConsentArtefacts())
                        .flatMap(consentArtefacts -> upsertConsentArtefacts(consentNotificationRequest).then());
            case DENIED:
                return processNotificationRequest(consentNotificationRequest, DENIED);
            default:
                return Mono.error(validationFailed());
        }
    }

    private Mono<Void> processNotificationRequest(ConsentNotificationRequest consentNotificationRequest,
                                                  ConsentStatus status) {
        return validateRequest(consentNotificationRequest.getConsentRequestId())
                .filter(consentRequest -> consentRequest.getStatus() == REQUESTED)
                .switchIfEmpty(Mono.error(new ClientError(CONFLICT,
                        new ErrorRepresentation(new Error(VALIDATION_FAILED,
                                "Consent request is already updated.")))))
                .flatMap(consentRequest -> consentRepository.updateConsent(consentRequest.getId(),
                        consentRequest.toBuilder().status(status).build()));
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
                    switch (consentNotificationRequest.getStatus()) {
                        case GRANTED:
                            return processGrantedConsent(consentArtefactReference,
                                    consentNotificationRequest.getConsentRequestId());
                        case REVOKED:
                            return processRevokedConsent(consentArtefactReference,
                                    consentNotificationRequest.getStatus(),
                                    consentNotificationRequest.getTimestamp());
                        case EXPIRED:
                            return processExpiredConsent(consentArtefactReference,
                                    consentNotificationRequest.getConsentRequestId(),
                                    consentNotificationRequest.getStatus(),
                                    consentNotificationRequest.getTimestamp());
                        default:
                            return processRejectedConsent(consentArtefactReference,
                                    consentNotificationRequest.getStatus(),
                                    consentNotificationRequest.getTimestamp());
                    }
                });
    }

    private Mono<Void> processExpiredConsent(ConsentArtefactReference consentArtefactReference,
                                             String consentRequestId, ConsentStatus status, Date timestamp) {
        return consentRepository.updateStatus(consentArtefactReference, status, timestamp)
                .then(dataFlowDeletePublisher.broadcastConsentExpiry(consentArtefactReference.getId(),
                        consentRequestId));
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
                .flatMap(consentArtefact -> consentRepository.getConsent(consentArtefact.getId(), ConsentStatus.GRANTED)
                        .switchIfEmpty(Mono.error(consentArtefactNotFound())))
                .collectList();
    }

    public Mono<Void> createRequest(String requesterId, ConsentRequestData consentRequestData) {
        return validateConsentRequest(consentRequestData)
                .then(sendConsentRequestToGateway(requesterId, consentRequestData));
    }

    private Mono<Void> sendConsentRequestToGateway(
            String requesterId,
            ConsentRequestData consentRequestData) {
        String cmSuffix = getCmSuffix(consentRequestData.getConsent());
        var requestDetail = consentRequestData.getConsent().to(
                requesterId,
                hiuProperties.getId(),
                conceptValidator);
        var gatewayRequestId = UUID.randomUUID();
        return centralRegistry.token()
                .flatMap(token -> {
                    return gatewayServiceClient.sendConsentRequest(
                            token, cmSuffix,
                            ConsentRequest.builder()
                                    .requestId(gatewayRequestId)
                                    .timestamp(java.time.Instant.now().toString())
                                    .consent(requestDetail)
                                    .build());
                }).then(consentRepository.insertConsentRequestToGateway(
                        consentRequestData.getConsent().toConsentRequest(gatewayRequestId.toString(), requesterId)));
    }

    private String getCmSuffix(Consent consent) {
        String[] parts = consent.getPatient().getId().split("@");
        return parts[1];
    }

    private Mono<Void> validateConsentRequest(String gatewayRequestId) {
        return consentRepository.consentRequest(gatewayRequestId).switchIfEmpty(Mono.error(consentRequestNotFound())).then();
    }

//    public Mono<Void> updatePostedRequest(ConsentRequestInitResponse consentRequestInitResponse) {
//
//        validateConsentRequest(consentRequestInitResponse.getResp().getRequestId())
//        consentRepository
//                .insert(consentRequestData.getConsent().toConsentRequest(
//                        consentCreationResponse.getId(),
//                        requesterId,
//                        hiuProperties.getConsentNotificationUrl()))
//                .then(Mono.fromCallable(consentCreationResponse::getId))
//        return null;
//    }
}