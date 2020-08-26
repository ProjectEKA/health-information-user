package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.UUID;

import static in.org.projecteka.hiu.common.TestBuilders.dateRange;
import static in.org.projecteka.hiu.common.TestBuilders.gatewayResponse;
import static in.org.projecteka.hiu.common.TestBuilders.string;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefact;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.consentStatusDetail;
import static in.org.projecteka.hiu.consent.TestBuilders.consentStatusRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.gatewayConsentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.hiuProperties;
import static in.org.projecteka.hiu.consent.TestBuilders.permission;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void shouldUpdateDBIfStatusResponseAndConsentRequestStatusAreDifferent() {
        var consentRequestDetail = consentStatusDetail().status(DENIED).build();
        var consentRequest = consentStatusRequest().consentRequest(consentRequestDetail).error(null).build();

        when(consentRepository
                .getConsentRequestStatus(consentRequest.getConsentRequest().getId())).thenReturn(Mono.just(GRANTED));
        when(consentRepository
                .updateConsentRequestStatus(consentRequest.getConsentRequest().getStatus(),
                        consentRequest.getConsentRequest().getId())).thenReturn(Mono.empty());

        var statusProducer = consentService.handleConsentRequestStatus(consentRequest);
        StepVerifier.create(statusProducer)
                .verifyComplete();
        verify(consentRepository).getConsentRequestStatus(consentRequest.getConsentRequest().getId());
        verify(consentRepository).updateConsentRequestStatus(consentRequest.getConsentRequest().getStatus(),
                consentRequest.getConsentRequest().getId());
    }

    @Test
    void shouldNotUpdateDBIfStatusResponseAndConsentRequestStatusAreEqual() {
        var consentRequestDetail = consentStatusDetail().status(GRANTED).build();
        var consentRequest = consentStatusRequest().consentRequest(consentRequestDetail).error(null).build();

        when(consentRepository
                .getConsentRequestStatus(consentRequest.getConsentRequest().getId())).thenReturn(Mono.just(GRANTED));

        var statusProducer = consentService.handleConsentRequestStatus(consentRequest);
        StepVerifier.create(statusProducer)
                .verifyComplete();
        verify(consentRepository).getConsentRequestStatus(consentRequest.getConsentRequest().getId());
    }
}
