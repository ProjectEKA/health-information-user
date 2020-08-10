package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.Consent;
import in.org.projecteka.hiu.consent.model.ConsentNotification;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentRequestInitResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.consent.model.GatewayConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.HIType;
import in.org.projecteka.hiu.consent.model.HiuConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.Patient;
import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import in.org.projecteka.hiu.consent.model.Permission;
import in.org.projecteka.hiu.consent.model.Purpose;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ErrorCode.INVALID_PURPOSE_OF_USE;
import static in.org.projecteka.hiu.ErrorCode.PATIENT_NOT_FOUND;
import static in.org.projecteka.hiu.common.Constants.EMPTY_STRING;
import static in.org.projecteka.hiu.common.Constants.PATIENT_REQUESTED_PURPOSE_CODE;
import static in.org.projecteka.hiu.common.Constants.STATUS;
import static in.org.projecteka.hiu.common.Constants.getCmSuffix;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.ERRORED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REVOKED;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.PARTIAL;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.SUCCEEDED;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.fromString;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;

public class ConsentService {
    private static final Logger logger = LoggerFactory.getLogger(ConsentService.class);
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final DataFlowDeletePublisher dataFlowDeletePublisher;
    private final PatientService patientService;
    private final HealthInformationPublisher healthInformationPublisher;
    private final ConceptValidator conceptValidator;
    private final GatewayServiceClient gatewayServiceClient;
    private final CacheAdapter<String, String> responseCache;
    private final Map<ConsentStatus, ConsentTask> consentTasks;
    private final PatientConsentRepository patientConsentRepository;
    private final CacheAdapter<String, String> patientRequestCache;
    private final ConsentServiceProperties consentServiceProperties;

    public ConsentService(HiuProperties hiuProperties,
                          ConsentRepository consentRepository,
                          DataFlowRequestPublisher dataFlowRequestPublisher,
                          DataFlowDeletePublisher dataFlowDeletePublisher,
                          PatientService patientService,
                          HealthInformationPublisher healthInformationPublisher,
                          ConceptValidator conceptValidator,
                          GatewayServiceClient gatewayServiceClient,
                          PatientConsentRepository patientConsentRepository,
                          ConsentServiceProperties consentServiceProperties,
                          CacheAdapter<String, String> patientRequestCache,
                          CacheAdapter<String, String> responseCache) {
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
        this.patientService = patientService;
        this.healthInformationPublisher = healthInformationPublisher;
        this.conceptValidator = conceptValidator;
        this.gatewayServiceClient = gatewayServiceClient;
        this.patientConsentRepository = patientConsentRepository;
        this.consentServiceProperties = consentServiceProperties;
        consentTasks = new HashMap<>();
        this.patientRequestCache = patientRequestCache;
        this.responseCache = responseCache;
    }

    private Mono<Void> validateConsentRequest(ConsentRequestData consentRequestData) {
        return conceptValidator.validatePurpose(consentRequestData.getConsent().getPurpose().getCode())
                .filter(result -> result)
                .switchIfEmpty(Mono.error(new ClientError(INTERNAL_SERVER_ERROR,
                        new ErrorRepresentation(new Error(INVALID_PURPOSE_OF_USE,
                                "Invalid Purpose Of Use")))))
                .then();
    }

    public Mono<Void> createRequest(String requesterId, ConsentRequestData consentRequestData) {
        var gatewayRequestId = UUID.randomUUID();
        return validateConsentRequest(consentRequestData)
                .then(sendConsentRequestToGateway(requesterId, consentRequestData, gatewayRequestId));
    }

    public Mono<Map<String, String>> handlePatientConsentRequest(String requesterId,
                                                                 PatientConsentRequest consentRequest) {
        Map<String, String> response = new HashMap<>();
        return Flux.fromIterable(filterEmptyAndNullValues(consentRequest.getHipIds()))
                .flatMap(hipId -> validatePatientConsentRequest(requesterId, hipId, consentRequest.isReloadConsent())
                        .flatMap(consentRequestData -> {
                            var dataRequestId = UUID.randomUUID();
                            var gatewayRequestId = UUID.randomUUID();
                            return validateConsentRequest(consentRequestData)
                                    .then(sendConsentRequestToGateway(requesterId, consentRequestData, gatewayRequestId))
                                    .then(patientConsentRepository.insertPatientConsentRequest(
                                            dataRequestId,
                                            hipId,
                                            requesterId)
                                            .doOnSuccess(discard -> response.put(hipId, dataRequestId.toString()))
                                            .doOnSuccess(discard -> patientRequestCache.put(gatewayRequestId.toString(),
                                                    dataRequestId.toString()).subscribe()));
                        }))
                .then(just(response));
    }


    private List<String> filterEmptyAndNullValues(List<String> ids) {
        return ids.stream().filter(Objects::nonNull).filter(n -> !n.equals(EMPTY_STRING)).collect(Collectors.toList());
    }

    private Mono<ConsentRequestData> handleForReloadConsent(String patientId, String hipId) {
        LocalDateTime now = now(UTC);

        return patientConsentRepository.deletePatientConsentRequestFor(patientId)
                .flatMap(patientConsentRepository::deleteConsentRequestFor)
                .flatMap(patientConsentRepository::deleteConsentArteFactFor)
                .flatMap(patientConsentRepository::deleteDataFlowRequestFor)
                .flatMap(patientConsentRepository::deleteHealthInformationFor)
                .flatMap(patientConsentRepository::deleteDataFlowPartsFor)
                .flatMap(patientConsentRepository::deleteDataFlowRequestKeysFor)
                .then(buildConsentRequest(patientId, hipId, now
                        .minusYears(consentServiceProperties.getConsentRequestFromYears())));
    }

    private Mono<ConsentRequestData> validatePatientConsentRequest(String requesterId, String hipId, boolean reloadConsent) {
        LocalDateTime now = now(UTC);
        if (reloadConsent) {
            return handleForReloadConsent(requesterId, hipId);
        }
        return patientConsentRepository.getConsentDetails(hipId, requesterId)
                .flatMap(consentData -> {
                    if (consentData.isEmpty()) {
                        return buildConsentRequest(requesterId, hipId, now
                                .minusYears(consentServiceProperties.getConsentRequestFromYears()));
                    }
                    for (Map<String, Object> consent : consentData) {
                        var consentArtefactId = consent.get("consentArtefactId");
                        var consentCreatedDate = (LocalDateTime) consent.get("dateCreated");
                        if (consentCreatedDate.isAfter(now.minusMinutes(consentServiceProperties.getConsentRequestDelay()))) {
                            return empty();
                        }
                        if (consentArtefactId != null) {
                            return patientConsentRepository.getDataFlowParts(consentArtefactId.toString())
                                    .flatMap(dataFlowParts -> {
                                        DateRange dateRange = (DateRange) consent.get("dateRange");
                                        var fromDate = dateRange.getFrom();
                                        if (dataFlowParts.isEmpty()) {
                                            var consentRequestId = consent.get("consentRequestId").toString();
                                            return consentRepository.consentRequestStatusFor(consentRequestId)
                                                    .flatMap(consentStatus -> {
                                                        if (consentStatus.equals(ERRORED)
                                                                || consentStatus.equals(EXPIRED)) {
                                                            return buildConsentRequest(requesterId, hipId, fromDate);
                                                        }
                                                        return empty();
                                                    });
                                        } else {
                                            for (Map<String, Object> dataFlowPart : dataFlowParts) {
                                                var latestResourceDate = (LocalDateTime) dataFlowPart.get("latestResourceDate");
                                                var dataFlowStatus = dataFlowPart.get(STATUS).toString();
                                                if (HealthInfoStatus.valueOf(dataFlowStatus).equals(SUCCEEDED)
                                                        || HealthInfoStatus.valueOf(dataFlowStatus).equals(PARTIAL)) {
                                                    return latestResourceDate == null
                                                            ? buildConsentRequest(requesterId, hipId, fromDate)
                                                            : buildConsentRequest(requesterId, hipId, latestResourceDate);
                                                }
                                                if (HealthInfoStatus.valueOf(dataFlowStatus)
                                                        .equals(HealthInfoStatus.ERRORED)) {
                                                    return buildConsentRequest(requesterId, hipId, fromDate);
                                                }
                                            }
                                        }
                                        return empty();
                                    });
                        }
                    }
                    return buildConsentRequest(requesterId,
                            hipId,
                            now.minusYears(consentServiceProperties.getConsentRequestFromYears()));
                });
    }

    private Mono<ConsentRequestData> buildConsentRequest(String requesterId, String hipId, LocalDateTime dateFrom) {
        return just(ConsentRequestData.builder().consent(Consent.builder()
                .hiTypes(List.of(HIType.class.getEnumConstants()))
                .patient(Patient.builder().id(requesterId).build())
                .permission(Permission.builder().dataEraseAt(now(UTC)
                        .plusMonths(consentServiceProperties.getConsentExpiryInMonths()))
                        .dateRange(DateRange.builder()
                                .from(dateFrom)
                                .to(now(UTC)).build())
                        .build())
                .purpose(new Purpose(PATIENT_REQUESTED_PURPOSE_CODE))
                .hipId(hipId)
                .build())
                .build());
    }

    private Mono<Void> sendConsentRequestToGateway(
            String requesterId,
            ConsentRequestData hiRequest,
            UUID gatewayRequestId) {
        var reqInfo = hiRequest.getConsent().to(requesterId, hiuProperties.getId(), conceptValidator);
        var patientId = hiRequest.getConsent().getPatient().getId();
        var consentRequest = ConsentRequest.builder()
                .requestId(gatewayRequestId)
                .timestamp(now(UTC))
                .consent(reqInfo)
                .build();
        var hiuConsentRequest = hiRequest.getConsent().toConsentRequest(gatewayRequestId.toString(), requesterId);
        return gatewayServiceClient.sendConsentRequest(getCmSuffix(patientId), consentRequest)
                .then(defer(() -> consentRepository.insertConsentRequestToGateway(hiuConsentRequest)));
    }

    public Mono<Void> updatePostedRequest(ConsentRequestInitResponse response) {
        var requestId = response.getResp().getRequestId();
        if (response.getError() != null) {
            logger.error("[ConsentService] Received error response from consent-request. HIU " +
                            "RequestId={}, Error code = {}, message={}",
                    requestId,
                    response.getError().getCode(),
                    response.getError().getMessage());
            return consentRepository.updateConsentRequestStatus(requestId, ERRORED, EMPTY_STRING);
        }

        if (response.getConsentRequest() != null) {
            var updatePublisher = consentRepository.consentRequestStatus(requestId)
                    .switchIfEmpty(error(consentRequestNotFound()))
                    .flatMap(status -> updateConsentRequestStatus(response, status));
            return patientRequestCache.get(requestId)
                    .switchIfEmpty(error(new NoSuchFieldError()))
                    .map(UUID::fromString)
                    .flatMap(dataRequestId -> {
                        var consentRequestId = fromString(response.getConsentRequest().getId());
                        return updatePublisher
                                .then(patientConsentRepository.updatePatientConsentRequest(dataRequestId,
                                        consentRequestId,
                                        now(UTC)));
                    })
                    .onErrorResume(NoSuchFieldError.class, e -> updatePublisher);
        }

        return error(ClientError.invalidDataFromGateway());
    }

    private Mono<Void> updateConsentRequestStatus(ConsentRequestInitResponse consentRequestInitResponse,
                                                  ConsentStatus oldStatus) {
        if (oldStatus.equals(ConsentStatus.POSTED)) {
            return consentRepository.updateConsentRequestStatus(
                    consentRequestInitResponse.getResp().getRequestId(),
                    ConsentStatus.REQUESTED,
                    consentRequestInitResponse.getConsentRequest().getId());
        }
        return empty();
    }

    public Flux<ConsentRequestRepresentation> requestsOf(String requesterId) {
        return consentRepository.requestsOf(requesterId)
                .take(consentServiceProperties.getDefaultPageSize())
                .collectList()
                .flatMapMany(list -> {
                    // Warming up cache
                    Set<String> patients = new java.util.HashSet<>(Set.of());
                    for (var result : list) {
                        var consentRequest =
                                (in.org.projecteka.hiu.consent.model.ConsentRequest) result.get("consentRequest");
                        patients.add(consentRequest.getPatient().getId());
                    }
                    return Flux.fromIterable(patients)
                            .flatMap(patientService::findPatientWith)
                            .thenMany(Flux.fromIterable(list));
                })
                .flatMap(result -> {
                    var consentRequest =
                            (in.org.projecteka.hiu.consent.model.ConsentRequest) result.get("consentRequest");
                    var consentRequestId = (String) result.get("consentRequestId");
                    consentRequestId = consentRequestId == null ? EMPTY_STRING : consentRequestId;
                    var status = (ConsentStatus) result.get(STATUS);
                    return Mono.zip(patientService.findPatientWith(consentRequest.getPatient().getId()),
                            mergeWithArtefactStatus(consentRequest, status, consentRequestId),
                            just(consentRequestId))
                            .onErrorResume(error -> error instanceof ClientError &&
                                            ((ClientError) error).getError().getError().getCode() == PATIENT_NOT_FOUND,
                                    error -> {
                                        logger.error("Consent request created for unknown user.");
                                        logger.error(error.getMessage(), error);
                                        return empty();
                                    });
                })
                .map(patientConsentRequest -> toConsentRequestRepresentation(patientConsentRequest.getT1(),
                        patientConsentRequest.getT2(),
                        patientConsentRequest.getT3()));
    }

    private Mono<in.org.projecteka.hiu.consent.model.ConsentRequest> mergeWithArtefactStatus(
            in.org.projecteka.hiu.consent.model.ConsentRequest consentRequest,
            ConsentStatus reqStatus,
            String consentRequestId) {
        var consent = consentRequest.toBuilder().status(reqStatus).build();
        return reqStatus.equals(ConsentStatus.POSTED)
                ? just(consent)
                : consentRepository.getConsentDetails(consentRequestId)
                .take(1)
                .next()
                .map(map -> ConsentStatus.valueOf(map.get(STATUS)))
                .switchIfEmpty(just(reqStatus))
                .map(artefactStatus -> consent.toBuilder().status(artefactStatus).build());
    }

    public Mono<Void> handleNotification(HiuConsentNotificationRequest hiuNotification) {
        return processConsentNotification(hiuNotification.getNotification(), hiuNotification.getTimestamp());
    }

    public Mono<Void> handleConsentArtefact(GatewayConsentArtefactResponse consentArtefactResponse) {
        if (consentArtefactResponse.getError() != null) {
            logger.error("[ConsentService] Received error response for consent-artefact. HIU " +
                            "RequestId={}, Error code = {}, message={}",
                    consentArtefactResponse.getResp().getRequestId(),
                    consentArtefactResponse.getError().getCode(),
                    consentArtefactResponse.getError().getMessage());
            return empty();
        }
        if (consentArtefactResponse.getConsent() != null) {
            return responseCache.get(consentArtefactResponse.getResp().getRequestId())
                    .flatMap(requestId -> consentRepository.insertConsentArtefact(
                            consentArtefactResponse.getConsent().getConsentDetail(),
                            consentArtefactResponse.getConsent().getStatus(),
                            requestId))
                    .then((defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                            consentArtefactResponse.getConsent().getConsentDetail().getConsentId(),
                            consentArtefactResponse.getConsent().getConsentDetail().getPermission().getDateRange(),
                            consentArtefactResponse.getConsent().getSignature(),
                            hiuProperties.getDataPushUrl()))));
        }
        return empty();
    }

    private Mono<Void> processConsentNotification(ConsentNotification notification, LocalDateTime localDateTime) {
        var consentTask = consentTasks.get(notification.getStatus());
        if (consentTask == null) {
            return error(ClientError.validationFailed());
        }
        return consentTask.perform(notification, localDateTime);
    }

    public Mono<List<Map<String, Object>>> getLatestCareContextResourceDates(String patientId, String hipId) {
        return patientConsentRepository.getLatestResourceDateByHipCareContext(patientId, hipId);
    }

    @PostConstruct
    private void postConstruct() {
        consentTasks.put(GRANTED, new GrantedConsentTask(consentRepository, gatewayServiceClient, responseCache));
        consentTasks.put(REVOKED, new RevokedConsentTask(consentRepository, healthInformationPublisher));
        consentTasks.put(EXPIRED, new ExpiredConsentTask(consentRepository, dataFlowDeletePublisher));
        consentTasks.put(DENIED, new DeniedConsentTask(consentRepository));
    }
}
