package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.Gateway;
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
import in.org.projecteka.hiu.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ErrorCode.INVALID_PURPOSE_OF_USE;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REVOKED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static reactor.core.publisher.Mono.error;


public class ConsentService {
    private static final Logger logger = LoggerFactory.getLogger(ConsentService.class);
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final DataFlowRequestPublisher dataFlowRequestPublisher;
    private final DataFlowDeletePublisher dataFlowDeletePublisher;
    private final PatientService patientService;
    private final Gateway gateway;
    private final HealthInformationPublisher healthInformationPublisher;
    private final ConceptValidator conceptValidator;
    private final GatewayServiceClient gatewayServiceClient;
    private Cache<String, String> gatewayResponseCache;
    private final Map<ConsentStatus, ConsentTask> consentTasks;
    private final PatientConsentRepository patientConsentRepository;
    private final Cache<String, String> patientRequestCache;
    private final ConsentServiceProperties consentServiceProperties;

    public ConsentService(HiuProperties hiuProperties,
                          ConsentRepository consentRepository,
                          DataFlowRequestPublisher dataFlowRequestPublisher,
                          DataFlowDeletePublisher dataFlowDeletePublisher,
                          PatientService patientService,
                          Gateway gateway,
                          HealthInformationPublisher healthInformationPublisher,
                          ConceptValidator conceptValidator,
                          GatewayServiceClient gatewayServiceClient,
                          PatientConsentRepository patientConsentRepository,
                          ConsentServiceProperties consentServiceProperties,
                          Cache<String, String> patientRequestCache) {
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
        this.patientService = patientService;
        this.gateway = gateway;
        this.healthInformationPublisher = healthInformationPublisher;
        this.conceptValidator = conceptValidator;
        this.gatewayServiceClient = gatewayServiceClient;
        this.patientConsentRepository = patientConsentRepository;
        this.consentServiceProperties = consentServiceProperties;
        consentTasks = new HashMap<>();
        this.patientRequestCache = patientRequestCache;
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

    public Mono<Map<String, String>> handlePatientConsentRequest(String requesterId, PatientConsentRequest consentRequest) {
        Map<String, String> response = new HashMap<>();
        return Flux.fromIterable(consentRequest.getHipIds())
                .flatMap(hipId -> buildConsentRequest(requesterId).flatMap(consentRequestData -> {
                    var dataRequestId = UUID.randomUUID();
                    var gatewayRequestId = UUID.randomUUID();
                    return validateConsentRequest(consentRequestData)
                            .then(sendConsentRequestToGateway(requesterId, consentRequestData, gatewayRequestId))
                            .then(patientConsentRepository.insertConsentRequestToGateway(consentRequest, dataRequestId, hipId)
                                    .doOnSuccess(discard -> response.put(hipId, dataRequestId.toString()))
                                    .doOnSuccess(discard -> patientRequestCache.put(gatewayRequestId.toString(), dataRequestId.toString())));
                })).then(Mono.just(response));
    }

    private Mono<ConsentRequestData> buildConsentRequest(String requesterId) {

        return Mono.just(ConsentRequestData.builder().consent(Consent.builder()
                .hiTypes(List.of(HIType.class.getEnumConstants()))
                .patient(Patient.builder().id(requesterId).build())
                .permission(Permission.builder().dataEraseAt(LocalDateTime.now(ZoneOffset.UTC).plusMonths(consentServiceProperties.getConsentExpiryInMonths()))
                        .dateRange(DateRange.builder().from(LocalDateTime.now(ZoneOffset.UTC).minusYears(consentServiceProperties.getConsentRequestFromYears()))
                                .to(LocalDateTime.now(ZoneOffset.UTC)).build()).build())
                .purpose(new Purpose("PATRQT"))
                .build())
                .build());
    }

    private Mono<Void> sendConsentRequestToGateway(
            String requesterId,
            ConsentRequestData hiRequest,
            UUID gatewayRequestId) {

        var reqInfo = hiRequest.getConsent().to(requesterId, hiuProperties.getId(), conceptValidator);
        return gateway.token()
                .flatMap(token -> gatewayServiceClient.sendConsentRequest(
                        token, getCmSuffix(hiRequest.getConsent()),
                        ConsentRequest.builder()
                                .requestId(gatewayRequestId)
                                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                                .consent(reqInfo)
                                .build()))
                .then(consentRepository.insertConsentRequestToGateway(
                        hiRequest.getConsent().toConsentRequest(gatewayRequestId.toString(), requesterId)));
    }

    private String getCmSuffix(Consent consent) {
        String[] parts = consent.getPatient().getId().split("@");
        return parts[1];
    }

    public Mono<Void> updatePostedRequest(ConsentRequestInitResponse response) {
        if (response.getError() != null) {
            logger.error(String.format("[ConsentService] Received error response from consent-request. HIU " +
                            "RequestId=%s, Error code = %d, message=%s",
                    response.getResp().getRequestId(),
                    response.getError().getCode(),
                    response.getError().getMessage()));
            return consentRepository.updateConsentRequestStatus(
                    response.getResp().getRequestId(),
                    ConsentStatus.ERRORED,
                    "");
        }

        if (response.getConsentRequest() != null) {
            var dataRequestId = patientRequestCache.asMap().get(response.getResp().getRequestId());
            var updatePublisher = consentRepository.consentRequestStatus(response.getResp().getRequestId())
                    .switchIfEmpty(error(consentRequestNotFound()))
                    .flatMap(status -> updateConsentRequestStatus(response, status));

            if (dataRequestId == null) {
                return updatePublisher;
            }
            var consentRequestId = UUID.fromString(response.getConsentRequest().getId());
            return updatePublisher
                    .then(patientConsentRepository
                            .insertPatientConsentRequestMapping(UUID.fromString(dataRequestId), consentRequestId));
        }

        return error(ClientError.invalidDataFromGateway());
    }

    private Mono<Void> updateConsentRequestStatus(ConsentRequestInitResponse
                                                          consentRequestInitResponse, ConsentStatus oldStatus) {
        if (oldStatus.equals(ConsentStatus.POSTED)) {
            return consentRepository.updateConsentRequestStatus(
                    consentRequestInitResponse.getResp().getRequestId(),
                    ConsentStatus.REQUESTED,
                    consentRequestInitResponse.getConsentRequest().getId());
        }
        return Mono.empty();
    }

    public Flux<ConsentRequestRepresentation> requestsOf(String requesterId) {
        return consentRepository.requestsOf(requesterId)
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
                    consentRequestId = consentRequestId == null ? "" : consentRequestId;
                    var status = (ConsentStatus) result.get("status");
                    return Mono.zip(patientService.findPatientWith(consentRequest.getPatient().getId()),
                            mergeWithArtefactStatus(consentRequest, status, consentRequestId),
                            Mono.just(consentRequestId));
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
                ? Mono.just(consent)
                : consentRepository.getConsentDetails(consentRequestId)
                .take(1)
                .next()
                .map(map -> ConsentStatus.valueOf(map.get("status")))
                .switchIfEmpty(Mono.just(reqStatus))
                .map(artefactStatus -> consent.toBuilder().status(artefactStatus).build());
    }

    public Mono<Void> handleNotification(HiuConsentNotificationRequest hiuNotification) {
        return processConsentNotification(hiuNotification.getNotification(), hiuNotification.getTimestamp());
    }

    public Mono<Void> handleConsentArtefact(GatewayConsentArtefactResponse consentArtefactResponse) {
        if (consentArtefactResponse.getError() != null) {
            logger.error(String.format("[ConsentService] Received error response for consent-artefact. HIU " +
                            "RequestId=%s, Error code = %d, message=%s",
                    consentArtefactResponse.getResp().getRequestId(),
                    consentArtefactResponse.getError().getCode(),
                    consentArtefactResponse.getError().getMessage()));
            return Mono.empty();
        }
        if (consentArtefactResponse.getConsent() != null) {
            return consentRepository.insertConsentArtefact(consentArtefactResponse.getConsent().getConsentDetail(),
                    consentArtefactResponse.getConsent().getStatus(),
                    gatewayResponseCache.asMap().get(consentArtefactResponse.getResp().getRequestId()))
                    .then((Mono.defer(() -> dataFlowRequestPublisher.broadcastDataFlowRequest(
                            consentArtefactResponse.getConsent().getConsentDetail().getConsentId(),
                            consentArtefactResponse.getConsent().getConsentDetail().getPermission().getDateRange(),
                            consentArtefactResponse.getConsent().getSignature(),
                            hiuProperties.getDataPushUrl()))));
        }
        return Mono.empty();
    }

    private Mono<Void> processConsentNotification(ConsentNotification notification, LocalDateTime localDateTime) {
        var consentTask = consentTasks.get(notification.getStatus());
        if (consentTask == null) {
            return error(ClientError.validationFailed());
        }
        return consentTask.perform(notification, localDateTime);
    }

    @PostConstruct
    private void postConstruct() {
        this.gatewayResponseCache = CacheBuilder
                .newBuilder()
                .maximumSize(50)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    public String load(String key) {
                        return "";
                    }
                });
        consentTasks.put(GRANTED, new GrantedConsentTask(
                consentRepository, gatewayServiceClient, gateway,
                gatewayResponseCache));
        consentTasks.put(REVOKED, new RevokedConsentTask(consentRepository, healthInformationPublisher));
        consentTasks.put(EXPIRED, new ExpiredConsentTask(consentRepository, dataFlowDeletePublisher));
        consentTasks.put(DENIED, new DeniedConsentTask(consentRepository));
    }
}
