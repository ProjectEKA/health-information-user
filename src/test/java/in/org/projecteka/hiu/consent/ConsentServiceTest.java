package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.Permission;
import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentNotificationRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.hiuProperties;
import static in.org.projecteka.hiu.consent.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.DENIED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.EXPIRED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static in.org.projecteka.hiu.dataflow.Utils.toDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.http.HttpStatus.CONFLICT;

public class ConsentServiceTest {
    @Mock
    Cache<String, Optional<Patient>> cache;
    @Mock
    private ConsentManagerClient consentManagerClient;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private DataFlowRequestPublisher dataFlowRequestPublisher;
    @Mock
    private DataFlowDeletePublisher dataFlowDeletePublisher;

    @Mock
    private PatientServiceClient patientServiceClient;
    @Mock
    private CentralRegistry centralRegistry;

    @Mock
    private HealthInformationPublisher healthInformationPublisher;
    @Mock
    private ConceptValidator conceptValidator;
    @Mock
    private GatewayServiceClient gatewayServiceClient;

    @Captor
    ArgumentCaptor<in.org.projecteka.hiu.consent.model.ConsentRequest> captor;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void shouldCreateConsentRequest() {
        String requesterId = randomString();
        var hiuProperties = hiuProperties().build();
        var token = randomString();
        ConsentService consentService = new ConsentService(
                consentManagerClient,
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null,
                null,
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        Permission permission = consentRequestData.getConsent().getPermission();
        permission.setDataEraseAt(toDate("2025-01-25T13:25:34.602"));
        permission.setDateRange(
                DateRange.builder().from(toDate("2014-01-25T13:25:34.602"))
                        .to(toDate("2015-01-25T13:25:34.602")).build());
        ConsentCreationResponse consentCreationResponse = consentCreationResponse().build();

        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(consentManagerClient.createConsentRequest(any(), eq(token)))
                .thenReturn(Mono.just(consentCreationResponse));
        when(consentRepository.insert(any()))
                .thenReturn(Mono.create(MonoSink::success));
        when(conceptValidator.validatePurpose(anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(consentService.create(requesterId, consentRequestData))
                .expectNext(consentCreationResponse)
                .verifyComplete();

        verify(consentRepository).insert(captor.capture());
        var request = captor.getValue();
        assertThat(request.getPermission().getDataEraseAt()).isEqualTo("2025-01-25T13:25:34.602");
        assertThat(request.getPermission().getDateRange().getFrom()).isEqualTo("2014-01-25T13:25:34.602");
        assertThat(request.getPermission().getDateRange().getTo()).isEqualTo("2015-01-25T13:25:34.602");
    }

    @Test
    void returnsRequestsFrom() {
        var requesterId = randomString();
        var token = randomString();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                new PatientService(patientServiceClient, cache, centralRegistry),
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        var patientRep = patientRepresentation().build();
        Permission permission = Permission.builder().dataEraseAt(toDate("2021-06-02T10:15:02.325")).build();
        var consentRequest = consentRequest()
                .createdDate(toDate("2020-06-02T10:15:02"))
                .status(ConsentStatus.REQUESTED)
                .patient(new in.org.projecteka.hiu.consent.model.Patient(patientRep.getIdentifier()))
                .permission(permission)
                .build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(consentRepository.getConsentDetails(consentRequest.getId())).thenReturn(Flux.empty());
        when(consentRepository.requestsFrom(requesterId)).thenReturn(Flux.just(consentRequest));
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(patientServiceClient.patientWith(consentRequest.getPatient().getId(), token))
                .thenReturn(Mono.just(patientRep));

        var consents = consentService.requestsFrom(requesterId);

        StepVerifier.create(consents)
                .assertNext(request -> assertThat(request.getStatus()).isEqualTo(ConsentStatus.REQUESTED))
                .verifyComplete();
    }

    @Test
    void returnsRequestsWithConsentArtefactStatus() {
        var requesterId = randomString();
        var token = randomString();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                new PatientService(patientServiceClient, cache, centralRegistry),
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        Permission permission = Permission.builder().dataEraseAt(toDate("2021-06-02T10:15:02.325")).build();
        //Permission permission = Permission.builder().dataEraseAt("2021-06-02T10:15:02.325Z").build();
        var patientRep = patientRepresentation().build();
        var consentRequest = consentRequest()
                .createdDate(toDate("2020-06-02T10:15:02"))
                .status(ConsentStatus.REQUESTED)
                .patient(new in.org.projecteka.hiu.consent.model.Patient(patientRep.getIdentifier()))
                .permission(permission)
                .build();
        var statusMap = new HashMap<String, String>();
        statusMap.put("status", "GRANTED");
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(consentRepository.getConsentDetails(consentRequest.getId())).thenReturn(Flux.just(statusMap));
        when(consentRepository.requestsFrom(requesterId)).thenReturn(Flux.just(consentRequest));
        when(patientServiceClient.patientWith(consentRequest.getPatient().getId(), token))
                .thenReturn(Mono.just(patientRep));

        var consents = consentService.requestsFrom(requesterId);

        StepVerifier.create(consents)
                .assertNext(request -> assertThat(request.getStatus()).isEqualTo(ConsentStatus.GRANTED))
                .verifyComplete();
    }

    @Test
    void handleDeniedConsentRequest() {
        var consentNotificationRequest = consentNotificationRequest().status(DENIED).build();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                new PatientService(patientServiceClient, cache, centralRegistry),
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        var consentRequest = consentRequest().id(consentNotificationRequest.getConsentRequestId());
        when(consentRepository.get(consentNotificationRequest.getConsentRequestId()))
                .thenReturn(Mono.just(consentRequest.status(REQUESTED).build()));
        when(consentRepository.updateConsent(consentNotificationRequest.getConsentRequestId(),
                consentRequest.status(DENIED).build()))
                .thenReturn(Mono.empty());

        var publisher = consentService.handleNotification(consentNotificationRequest);

        StepVerifier.create(publisher)
                .verifyComplete();
    }

    @Test
    void handleExpiredConsentRequest() {
        var consentNotificationRequest = consentNotificationRequest()
                .status(EXPIRED)
                .consentArtefacts(Collections.emptyList())
                .build();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                new PatientService(patientServiceClient, cache, centralRegistry),
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        var consentRequest = consentRequest().id(consentNotificationRequest.getConsentRequestId());
        when(consentRepository.get(consentNotificationRequest.getConsentRequestId()))
                .thenReturn(Mono.just(consentRequest.status(REQUESTED).build()));
        when(consentRepository.updateConsent(consentNotificationRequest.getConsentRequestId(),
                consentRequest.status(EXPIRED).build()))
                .thenReturn(Mono.empty());

        var publisher = consentService.handleNotification(consentNotificationRequest);

        StepVerifier.create(publisher)
                .verifyComplete();
    }

    @Test
    void returnConflictWhenConsentRequestAlreadyUpdated() {
        var consentNotificationRequest = consentNotificationRequest().status(DENIED).build();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                dataFlowDeletePublisher,
                new PatientService(patientServiceClient, cache, centralRegistry),
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        var consentRequest = consentRequest()
                .status(DENIED)
                .id(consentNotificationRequest.getConsentRequestId())
                .build();
        when(consentRepository.get(consentNotificationRequest.getConsentRequestId()))
                .thenReturn(Mono.just(consentRequest));

        var publisher = consentService.handleNotification(consentNotificationRequest);

        StepVerifier.create(publisher)
                .expectErrorMatches(e -> (e instanceof ClientError) &&
                        ((ClientError) e).getHttpStatus() == CONFLICT)
                .verify();
    }

    @Test
    public void shouldSaveAndPostConsentRequest() {
        String requesterId = randomString();
        var hiuProperties = hiuProperties().build();
        var token = randomString();
        ConsentService consentService = new ConsentService(
                consentManagerClient,
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null,
                null,
                centralRegistry,
                healthInformationPublisher,
                conceptValidator,
                gatewayServiceClient);
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        consentRequestData.getConsent().getPatient().setId("hinapatel79@ncg");
        when(conceptValidator.validatePurpose(anyString())).thenReturn(Mono.just(true));
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(gatewayServiceClient.sendConsentRequest(eq(token), anyString(), any()))
                .thenReturn(Mono.empty());
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));
        StepVerifier.create(consentService.createRequest(requesterId, consentRequestData))
                .expectComplete().verify();
    }
}
