package in.org.projecteka.hiu.consent;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.consent.model.ConsentCreationResponse;
import in.org.projecteka.hiu.consent.model.ConsentRequestData;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.Permission;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRequest;
import in.org.projecteka.hiu.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.hiuProperties;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ConsentServiceTest {
    @Mock
    private ConsentManagerClient consentManagerClient;

    @Mock
    private ConsentRepository consentRepository;

    @Mock
    private DataFlowRequestPublisher dataFlowRequestPublisher;

    @Mock
    private PatientServiceClient patientServiceClient;

    @Mock
    Cache<String, Optional<Patient>> cache;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void shouldCreateConsentRequest() {
        String requesterId = randomString();
        var hiuProperties = hiuProperties().build();
        ConsentService consentService = new ConsentService(
                consentManagerClient,
                hiuProperties,
                consentRepository,
                dataFlowRequestPublisher,
                null);
        ConsentRequestData consentRequestData = consentRequestDetails().build();
        ConsentCreationResponse consentCreationResponse = consentCreationResponse().build();
        ConsentRequest consentRequest = new ConsentRequest(consentRequestData.getConsent()
                .to(requesterId, hiuProperties.getId(), hiuProperties.getName(), hiuProperties.getCallBackUrl()));

        when(consentManagerClient.createConsentRequest(consentRequest)).thenReturn(Mono.just(consentCreationResponse));
        when(consentRepository.insert(consentRequestData.getConsent().toConsentRequest(
                consentCreationResponse.getId(),
                requesterId, hiuProperties.getCallBackUrl())))
                .thenReturn(Mono.create(MonoSink::success));

        StepVerifier.create(consentService.create(requesterId, consentRequestData))
                .expectNext(consentCreationResponse)
                .verifyComplete();
    }

    @Test
    void returnsRequestsFrom() {
        var requesterId = randomString();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                new PatientService(patientServiceClient, cache));
        Permission permission = Permission.builder().dataExpiryAt("2021-06-02T10:15:02.325Z").build();
        Patient patient = patient().build();
        var consentRequest = consentRequest()
                .createdDate("2020-06-02T10:15:02Z")
                .status(ConsentStatus.REQUESTED)
                .patient(new in.org.projecteka.hiu.consent.model.Patient(patient.getIdentifier()))
                .permission(permission)
                .build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(consentRepository.getConsentStatus(consentRequest.getId())).thenReturn(Mono.empty());
        when(consentRepository.requestsFrom(requesterId)).thenReturn(Flux.just(consentRequest));
        when(patientServiceClient.patientWith(consentRequest.getPatient().getId()))
                .thenReturn(Mono.just(patient));

        var consents = consentService.requestsFrom(requesterId);

        StepVerifier.create(consents)
                .assertNext(request -> assertThat(request.getStatus()).isEqualTo(ConsentStatus.REQUESTED))
                .verifyComplete();
    }


    @Test
    void returnsRequestsWithConsentArtefactStatus() {
        var requesterId = randomString();
        var consentService = new ConsentService(
                consentManagerClient,
                hiuProperties().build(),
                consentRepository,
                dataFlowRequestPublisher,
                new PatientService(patientServiceClient, cache));
        Permission permission = Permission.builder().dataExpiryAt("2021-06-02T10:15:02.325Z").build();
        Patient patient = patient().build();
        var consentRequest = consentRequest()
                .createdDate("2020-06-02T10:15:02Z")
                .status(ConsentStatus.REQUESTED)
                .patient(new in.org.projecteka.hiu.consent.model.Patient(patient.getIdentifier()))
                .permission(permission)
                .build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(consentRepository.getConsentStatus(consentRequest.getId())).thenReturn(Mono.just("GRANTED"));
        when(consentRepository.requestsFrom(requesterId)).thenReturn(Flux.just(consentRequest));
        when(patientServiceClient.patientWith(consentRequest.getPatient().getId()))
                .thenReturn(Mono.just(patient));

        var consents = consentService.requestsFrom(requesterId);

        StepVerifier.create(consents)
                .assertNext(request -> assertThat(request.getStatus()).isEqualTo(ConsentStatus.GRANTED))
                .verifyComplete();
    }
}
