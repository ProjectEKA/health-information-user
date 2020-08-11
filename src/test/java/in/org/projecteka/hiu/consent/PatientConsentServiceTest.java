package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.dataflow.HealthInfoManager;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatus;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInfoStatus;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static in.org.projecteka.hiu.common.TestBuilders.dateRange;
import static in.org.projecteka.hiu.common.TestBuilders.gatewayResponse;
import static in.org.projecteka.hiu.common.TestBuilders.string;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefact;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.gatewayConsentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.hiuProperties;
import static in.org.projecteka.hiu.consent.TestBuilders.permission;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static java.time.ZoneOffset.UTC;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

class PatientConsentServiceTest {
    @Mock
    CacheAdapter<String, Patient> cache;
    @Mock
    CacheAdapter<String, String> patientRequestCache;
    @Mock
    CacheAdapter<String, PatientSearchGatewayResponse> patientSearchCache;
    @Mock
    CacheAdapter<String, String> gatewayCache;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private PatientConsentRepository patientConsentRepository;
    @Mock
    private ConsentServiceProperties consentServiceProperties;
    @Mock
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    @Mock
    private DataFlowDeletePublisher dataFlowDeletePublisher;
    @Mock
    private HealthInformationPublisher healthInformationPublisher;
    @Mock
    private ConceptValidator conceptValidator;
    @Mock
    private GatewayServiceClient gatewayServiceClient;
    @Mock
    private HiuProperties hiuProperties;
    @Mock
    private HealthInfoManager healthInfoManager;
    private PatientConsentService consentService;


    @BeforeEach
    void setUp() {
        initMocks(this);
        var hiuProperties = hiuProperties().build();
        Function<List<String>, Flux<PatientHealthInfoStatus>> healthInfoStatus = healthInfoManager::fetchHealthInformationStatus;
        consentService = new PatientConsentService(
                consentServiceProperties,
                hiuProperties,
                conceptValidator,
                patientRequestCache,
                consentRepository,
                patientConsentRepository,
                gatewayServiceClient,
                healthInfoStatus);
    }

    @Test
    void shouldHandleConsentArtefactResponse() throws NoSuchFieldException {
        var requestId = UUID.randomUUID();
        var consentRequestId = UUID.randomUUID().toString();
        ConsentService consentService = new ConsentService(
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null,
                null,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient,
                patientConsentRepository,
                consentServiceProperties,
                patientRequestCache,
                gatewayCache);
        var consentId = UUID.randomUUID().toString();
        var dateRange = dateRange().build();
        var signature = string();
        var dataPushUrl = string();
        var gatewayResponse = gatewayResponse().requestId(requestId.toString()).build();
        var permission = permission().dateRange(dateRange).build();
        var consentDetail = consentArtefact().permission(permission).consentId(consentId).build();
        var consentArtefactResponse = consentArtefactResponse()
                .consentDetail(consentDetail)
                .signature(signature)
                .status(GRANTED)
                .build();
        var gatewayConsentArtefactResponse = gatewayConsentArtefactResponse()
                .consent(consentArtefactResponse)
                .error(null)
                .resp(gatewayResponse)
                .build();
        when(gatewayCache.get(requestId.toString())).thenReturn(just(consentRequestId));
        when(consentRepository.insertConsentArtefact(consentDetail, GRANTED, consentRequestId)).thenReturn(empty());
        when(hiuProperties.getDataPushUrl()).thenReturn(dataPushUrl);
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(consentId, dateRange, signature, dataPushUrl))
                .thenReturn(empty());

        var publisher = consentService.handleConsentArtefact(gatewayConsentArtefactResponse);

        StepVerifier.create(publisher).expectComplete().verify();
        verify(consentRepository).insertConsentArtefact(consentDetail, GRANTED, consentRequestId);
        verify(dataFlowRequestPublisher).broadcastDataFlowRequest(consentId, dateRange, signature, dataPushUrl);
    }

    @Test
    void shouldBuildFirstConsentRequestIfConsentDataIsEmpty() {
        var requesterId = "hinapatel@ncg";
        var hiuProperties = hiuProperties().build();
        var token = randomString();
        var hipId = string();
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        consentRequestData.getConsent().getPatient().setId(requesterId);
        //when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(empty());
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getLatestDataRequestsForPatient(eq(requesterId), any())).thenReturn(Mono.just(new ArrayList<>()));
        when(patientConsentRepository.insertPatientConsentRequest(any(),eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(healthInfoManager.fetchHealthInformationStatus(any())).thenReturn(Flux.empty());
        when(patientRequestCache.put(any(),any())).thenReturn(Mono.empty());


        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();
    }

    @Test
    void shouldMakeConsentRequestIfPreviousDataRequestIsNotProcessing() {
        var requesterId = "hinapatel@ncg";
        var hipId = string();

        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .hipId(hipId)
                .patientDataRequestedAt(LocalDateTime.now(UTC))
                .dataRequestId(UUID.randomUUID().toString())
                .build();

        PatientHealthInfoStatus hipRequestErrorStatus = PatientHealthInfoStatus.builder()
                .hipId(hipId)
                .requestId(dataRequestDetail.getDataRequestId())
                .status(DataRequestStatus.ERRORED)
                .build();
        when(healthInfoManager.fetchHealthInformationStatus(any())).thenReturn(Flux.just(hipRequestErrorStatus));
        when(patientConsentRepository.getLatestDataRequestsForPatient(eq(requesterId), any())).thenReturn(Mono.just(List.of(dataRequestDetail)));

        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(1)).sendConsentRequest(eq("ncg"), capture.capture());
        assertEquals(hipId, capture.getValue().getConsent().getHip().getId());
        assertEquals(requesterId, capture.getValue().getConsent().getPatient().getId());
    }

    @Test
    void shouldNotMakeConsentRequestIfPreviousDataRequestIsProcessing() {
        var requesterId = "hinapatel@ncg";
        var hipId = string();

        var dataRequestDetail = TestBuilders.patientDataRequestDetail()
                .hipId(hipId)
                .patientDataRequestedAt(LocalDateTime.now(UTC))
                .dataRequestId(UUID.randomUUID().toString())
                .build();

        PatientHealthInfoStatus hipRequestErrorStatus = PatientHealthInfoStatus.builder()
                .hipId(hipId)
                .requestId(dataRequestDetail.getDataRequestId())
                .status(DataRequestStatus.PROCESSING)
                .build();
        when(healthInfoManager.fetchHealthInformationStatus(any())).thenReturn(Flux.just(hipRequestErrorStatus));
        when(patientConsentRepository.getLatestDataRequestsForPatient(eq(requesterId), any())).thenReturn(Mono.just(List.of(dataRequestDetail)));

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request)
                .expectNext(Map.of())
                .verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(0)).sendConsentRequest(eq("ncg"), capture.capture());
    }

}
