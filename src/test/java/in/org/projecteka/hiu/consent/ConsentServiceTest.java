package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.consent.model.GatewayConsentArtefactResponse;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.FieldSetter;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.hiuProperties;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ConsentServiceTest {
    @Mock
    Cache<String, Optional<Patient>> cache;
    @Mock
    Cache<String, Optional<PatientSearchGatewayResponse>> patientSearchCache;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    @Mock
    private DataFlowDeletePublisher dataFlowDeletePublisher;
    @Mock
    private Gateway gateway;
    @Mock
    private HealthInformationPublisher healthInformationPublisher;
    @Mock
    private ConceptValidator conceptValidator;
    @Mock
    private GatewayServiceClient gatewayServiceClient;
    @Mock
    private HiuProperties hiuProperties;
    @Mock
    private GatewayConsentArtefactResponse gatewayConsentArtefactResponse;
    @Mock
    private ConsentArtefactResponse consentArtefactResponse;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void shouldSaveAndPostConsentRequest() {
        String requesterId = randomString();
        var hiuProperties = hiuProperties().build();
        var token = randomString();
        ConsentService consentService = new ConsentService(
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null,
                null,
                gateway,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        consentRequestData.getConsent().getPatient().setId("hinapatel79@ncg");
        when(conceptValidator.validatePurpose(anyString())).thenReturn(Mono.just(true));
        when(gateway.token()).thenReturn(Mono.just(token));
        when(gatewayServiceClient.sendConsentRequest(eq(token), anyString(), any()))
                .thenReturn(Mono.empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));

        Mono<Void> request = consentService.createRequest(requesterId, consentRequestData);

        StepVerifier.create(request).expectComplete().verify();
    }

    @Test
    void shouldHandleConsentArtefactResponse() throws NoSuchFieldException {
        var requestId = UUID.randomUUID();
        var mockCache = Mockito.mock(Cache.class);
        var mockGatewayResponse = Mockito.mock(GatewayResponse.class);
        var consentRequestId = UUID.randomUUID().toString();
        ConsentService consentService = new ConsentService(
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null,
                null,
                gateway,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        FieldSetter.setField(consentService,
                consentService.getClass().getDeclaredField("gatewayResponseCache"), mockCache);
        var consentDetail = Mockito.mock(ConsentArtefact.class);
        var consentId = UUID.randomUUID().toString();
        var cacheMap = new ConcurrentHashMap<>();
        cacheMap.put(requestId.toString(), consentRequestId);
        var dateRange = Mockito.mock(DateRange.class);
        var signature = "temp";
        var dataPushUrl = "tempUrl";
        var permission = Mockito.mock(in.org.projecteka.hiu.consent.model.consentmanager.Permission.class);
        when(gatewayConsentArtefactResponse.getConsent()).thenReturn(consentArtefactResponse);
        when(gatewayConsentArtefactResponse.getResp()).thenReturn(mockGatewayResponse);
        when(consentArtefactResponse.getConsentDetail()).thenReturn(consentDetail);
        when(consentDetail.getConsentId()).thenReturn(consentId);
        when(consentDetail.getPermission()).thenReturn(permission);
        when(permission.getDateRange()).thenReturn(dateRange);
        when(consentArtefactResponse.getSignature()).thenReturn(signature);
        when(consentArtefactResponse.getStatus()).thenReturn(GRANTED);
        when(mockCache.asMap()).thenReturn(cacheMap);
        when(mockGatewayResponse.getRequestId()).thenReturn(requestId.toString());
        when(consentRepository.insertConsentArtefact(consentDetail, GRANTED, consentRequestId)).thenReturn(Mono.empty());
        when(hiuProperties.getDataPushUrl()).thenReturn(dataPushUrl);
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(consentId, dateRange, signature, dataPushUrl))
                .thenReturn(Mono.empty());

        Mono<Void> publisher = consentService.handleConsentArtefact(gatewayConsentArtefactResponse);

        StepVerifier.create(publisher).expectComplete().verify();
        verify(consentRepository).insertConsentArtefact(consentDetail, GRANTED, consentRequestId);
        verify(dataFlowRequestPublisher).broadcastDataFlowRequest(consentId, dateRange, signature, dataPushUrl);
    }
}
