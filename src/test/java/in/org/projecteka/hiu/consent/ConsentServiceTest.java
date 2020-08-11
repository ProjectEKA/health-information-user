package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

class ConsentServiceTest {
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
    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        initMocks(this);
        var hiuProperties = hiuProperties().build();
        consentService = new ConsentService(
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
    }

    @Test
    void shouldSaveAndPostConsentRequest() {
        String requesterId = randomString();
        var token = randomString();
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        consentRequestData.getConsent().getPatient().setId("hinapatel79@ncg");

        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any()))
                .thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));

        Mono<Void> request = consentService.createRequest(requesterId, consentRequestData);

        StepVerifier.create(request).expectComplete().verify();
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
        var requesterId = string();
        var token = randomString();
        var hipId = string();
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        consentRequestData.getConsent().getPatient().setId("hinapatel79@ncg");

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(empty());
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();
    }

    @Test
    void shouldNotMakeConsentRequestWhenDataflowPartIsEmptyForConsentArtefact() {
        var requesterId = "hinapatel@ndhm";
        var token = randomString();
        var hipId = string();

        String consentArtefactId = string();
        String consentRequestId = string();
        LocalDateTime dateCreated = LocalDateTime.now(UTC);
        DateRange dateRange = new DateRange(LocalDateTime.of(2019, 1, 1, 0, 0), LocalDateTime.of(2019, 12, 31, 0, 0));
        List<Map<String, Object>> consentDetails = asList(createConsentDetail(consentArtefactId, consentRequestId, dateCreated, dateRange));

        LocalDateTime lastResourceDate = LocalDateTime.of(2019, 10, 1, 0, 0);
        List<Map<String, Object>> dataFlowParts = getDataFlowParts(lastResourceDate, HealthInfoStatus.ERRORED.toString());

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(Mono.just(consentDetails));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getDataFlowParts(consentArtefactId)).thenReturn(Mono.empty());
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        verify(gatewayServiceClient, never()).sendConsentRequest(anyString(), any(ConsentRequest.class));
    }

    @Test
    void shouldMakeConsentRequestWhenDataflowPartIsEmptyForConsentArtefactButConsentRequestStatusIsErrored() {
        var requesterId = "hinapatel@ndhm";
        var token = randomString();
        var hipId = string();

        String consentArtefactId = string();
        String consentRequestId = string();
        LocalDateTime dateCreated = LocalDateTime.now(UTC);
        DateRange dateRange = new DateRange(LocalDateTime.of(2019, 1, 1, 0, 0), LocalDateTime.of(2019, 12, 31, 0, 0));
        List<Map<String, Object>> consentDetails = asList(createConsentDetail(consentArtefactId, consentRequestId, dateCreated, dateRange));

        LocalDateTime lastResourceDate = LocalDateTime.of(2019, 10, 1, 0, 0);
        List<Map<String, Object>> dataFlowParts = getDataFlowParts(lastResourceDate, HealthInfoStatus.ERRORED.toString());

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(Mono.just(consentDetails));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getDataFlowParts(consentArtefactId)).thenReturn(Mono.just(Collections.emptyList()));
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());
        when(consentRepository.consentRequestStatusFor(consentRequestId)).thenReturn(Mono.just(ConsentStatus.ERRORED));

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(1)).sendConsentRequest(eq("ndhm"), capture.capture());
        assertEquals(hipId, capture.getValue().getConsent().getHip().getId());
        assertEquals(requesterId, capture.getValue().getConsent().getPatient().getId());
        assertEquals(dateRange.getFrom(), capture.getValue().getConsent().getPermission().getDateRange().getFrom());
    }

    @Test
    void shouldMakeConsentRequestFromPreviousDataflowPartLastResourceDate() {
        var requesterId = "hinapatel@ndhm";
        var token = randomString();
        var hipId = string();

        String consentArtefactId = string();
        String consentRequestId = string();
        LocalDateTime dateCreated = LocalDateTime.now(UTC);
        DateRange dateRange = new DateRange(LocalDateTime.of(2019, 1, 1, 0, 0), LocalDateTime.of(2019, 12, 31, 0, 0));
        List<Map<String, Object>> consentDetails = asList(createConsentDetail(consentArtefactId, consentRequestId, dateCreated, dateRange));

        LocalDateTime lastResourceDate = LocalDateTime.of(2019, 10, 1, 0, 0);
        List<Map<String, Object>> dataFlowParts = getDataFlowParts(lastResourceDate, HealthInfoStatus.SUCCEEDED.toString());

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(Mono.just(consentDetails));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getDataFlowParts(consentArtefactId)).thenReturn(Mono.just(dataFlowParts));
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(1)).sendConsentRequest(eq("ndhm"), capture.capture());
        assertEquals(hipId, capture.getValue().getConsent().getHip().getId());
        assertEquals(requesterId, capture.getValue().getConsent().getPatient().getId());
        assertEquals(lastResourceDate, capture.getValue().getConsent().getPermission().getDateRange().getFrom());
    }

    @Test
    void shouldMakeConsentRequestFromConsentDetailsWhenPreviousDataflowPartHasNoLastResourceDate() {
        var requesterId = "hinapatel@ndhm";
        var token = randomString();
        var hipId = string();

        String consentArtefactId = string();
        String consentRequestId = string();
        LocalDateTime dateCreated = LocalDateTime.now(UTC);
        DateRange dateRange = new DateRange(LocalDateTime.of(2019, 1, 1, 0, 0), LocalDateTime.of(2019, 12, 31, 0, 0));
        List<Map<String, Object>> consentDetails = asList(createConsentDetail(consentArtefactId, consentRequestId, dateCreated, dateRange));

        LocalDateTime lastResourceDate = LocalDateTime.of(2019, 10, 1, 0, 0);
        List<Map<String, Object>> dataFlowParts = getDataFlowParts(null, HealthInfoStatus.SUCCEEDED.toString());

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(Mono.just(consentDetails));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getDataFlowParts(consentArtefactId)).thenReturn(Mono.just(dataFlowParts));
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(1)).sendConsentRequest(eq("ndhm"), capture.capture());
        assertEquals(hipId, capture.getValue().getConsent().getHip().getId());
        assertEquals(requesterId, capture.getValue().getConsent().getPatient().getId());
        assertEquals(dateRange.getFrom(), capture.getValue().getConsent().getPermission().getDateRange().getFrom());
    }

    @Test
    void shouldMakeConsentRequestFromConsentDetailsWhenPreviousDataflowPartHasError() {
        var requesterId = "hinapatel@ndhm";
        var token = randomString();
        var hipId = string();

        String consentArtefactId = string();
        String consentRequestId = string();
        LocalDateTime dateCreated = LocalDateTime.now(UTC);
        DateRange dateRange = new DateRange(LocalDateTime.of(2019, 1, 1, 0, 0), LocalDateTime.of(2019, 12, 31, 0, 0));
        List<Map<String, Object>> consentDetails = asList(createConsentDetail(consentArtefactId, consentRequestId, dateCreated, dateRange));

        LocalDateTime lastResourceDate = LocalDateTime.of(2019, 10, 1, 0, 0);
        List<Map<String, Object>> dataFlowParts = getDataFlowParts(lastResourceDate, HealthInfoStatus.ERRORED.toString());

        when(patientConsentRepository.getConsentDetails(hipId, requesterId)).thenReturn(Mono.just(consentDetails));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(gatewayServiceClient.sendConsentRequest(anyString(), any())).thenReturn(empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(empty());
        when(patientConsentRepository.getDataFlowParts(consentArtefactId)).thenReturn(Mono.just(dataFlowParts));
        when(patientConsentRepository.insertPatientConsentRequest(any(UUID.class), eq(hipId), eq(requesterId))).thenReturn(Mono.empty());
        when(patientRequestCache.put(anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Map<String, String>> request = consentService.handlePatientConsentRequest(requesterId,
                new PatientConsentRequest(List.of(hipId), false));

        StepVerifier.create(request).expectNextCount(1).verifyComplete();

        ArgumentCaptor<ConsentRequest> capture = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(gatewayServiceClient, times(1)).sendConsentRequest(eq("ndhm"), capture.capture());
        assertEquals(hipId, capture.getValue().getConsent().getHip().getId());
        assertEquals(requesterId, capture.getValue().getConsent().getPatient().getId());
        assertEquals(dateRange.getFrom(), capture.getValue().getConsent().getPermission().getDateRange().getFrom());
    }

    private List<Map<String, Object>> getDataFlowParts(LocalDateTime lastResourceDate, String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("latestResourceDate", lastResourceDate);
        map.put("status", status);
        map.put("dataFlowPartNumber", 1);
        return asList(map);
    }

    private Map<String, Object> createConsentDetail(String consentArtefactId, String consentRequestId, LocalDateTime dateCreated1, DateRange dateRange) {
        Map<String, Object> map = new HashMap<>();
        map.put("consentArtefactId", consentArtefactId);
        map.put("dateCreated", dateCreated1);
        map.put("consentRequestId", consentRequestId);
        map.put("dateRange", dateRange);
        return map;
    }
}
