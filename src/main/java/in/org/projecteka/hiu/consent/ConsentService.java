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
import in.org.projecteka.hiu.consent.model.GatewayConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.HiuConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ErrorCode.INVALID_PURPOSE_OF_USE;
import static in.org.projecteka.hiu.consent.model.ConsentRequestRepresentation.toConsentRequestRepresentation;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REVOKED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

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

    public ConsentService(HiuProperties hiuProperties,
                          ConsentRepository consentRepository,
                          DataFlowRequestPublisher dataFlowRequestPublisher,
                          DataFlowDeletePublisher dataFlowDeletePublisher,
                          PatientService patientService,
                          Gateway gateway,
                          HealthInformationPublisher healthInformationPublisher,
                          ConceptValidator conceptValidator,
                          GatewayServiceClient gatewayServiceClient) {
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
        this.dataFlowRequestPublisher = dataFlowRequestPublisher;
        this.dataFlowDeletePublisher = dataFlowDeletePublisher;
        this.patientService = patientService;
        this.gateway = gateway;
        this.healthInformationPublisher = healthInformationPublisher;
        this.conceptValidator = conceptValidator;
        this.gatewayServiceClient = gatewayServiceClient;
        consentTasks = new HashMap<>();
    }

    private Mono<Void> validateConsentRequest(ConsentRequestData consentRequestData) {
        return conceptValidator.validatePurpose(consentRequestData.getConsent().getPurpose().getCode())
                .filter(result -> result)
                .switchIfEmpty(Mono.error(new ClientError(BAD_REQUEST,
                        new ErrorRepresentation(new Error(INVALID_PURPOSE_OF_USE,
                                "Invalid Purpose Of Use")))))
                .then();
    }

    public Mono<Void> createRequest(String requesterId, ConsentRequestData consentRequestData) {
        return validateConsentRequest(consentRequestData)
                .then(sendConsentRequestToGateway(requesterId, consentRequestData));
    }

    private Mono<Void> sendConsentRequestToGateway(
            String requesterId,
            ConsentRequestData hiRequest) {
        var reqInfo = hiRequest.getConsent().to(requesterId, hiuProperties.getId(), conceptValidator);
        var gatewayRequestId = UUID.randomUUID();
        return gateway.token()
                .flatMap(token -> gatewayServiceClient.sendConsentRequest(
                        token, getCmSuffix(hiRequest.getConsent()),
                        ConsentRequest.builder()
                                .requestId(gatewayRequestId)
                                .timestamp(java.time.Instant.now().toString())
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
            return consentRepository.consentRequestStatus(response.getResp().getRequestId())
                    .switchIfEmpty(Mono.error(consentRequestNotFound()))
                    .flatMap(status -> updateConsentRequestStatus(response, status));
        }

        return Mono.error(ClientError.invalidDataFromGateway());
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
                       .switchIfEmpty(Mono.just(consentRequest.getStatus()))
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
            return Mono.error(ClientError.validationFailed());
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
