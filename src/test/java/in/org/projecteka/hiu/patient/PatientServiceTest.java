package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class PatientServiceTest {

    @Mock
    Cache<String, Optional<Patient>> cache;

    @Mock
    Cache<String, Optional<PatientSearchGatewayResponse>> patientSearchCache;

    @Mock
    Gateway gateway;

    @Mock
    HiuProperties hiuProperties;

    @Mock
    GatewayProperties gatewayProperties;

    @Mock
    GatewayServiceClient gatewayServiceClient;

    @BeforeEach
    void init() {
        initMocks(this);
    }

    @Test
    void returnPatientFromCacheForFindPatient() {
        var patientId = randomString();
        var token = randomString();
        var patient = patient().build();
        var map = new ConcurrentHashMap<String, Optional<Patient>>();
        map.put(patientId, Optional.of(patient));
        when(cache.asMap()).thenReturn(map);
        when(gateway.token()).thenReturn(Mono.just(token));
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);

        Mono<Patient> patientPublisher = patientService.findPatientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(gatewayServiceClient, never()).findPatientWith(any(), any());
    }

    @Test
    void shouldReturnGatewayTimeoutWhenNoResponseFromGatewayWithinTimeLimit() {
        var patientId = "temp@ncg";
        var token = randomString();

        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);

        when(hiuProperties.getId()).thenReturn("10000005");
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(gateway.token()).thenReturn(Mono.just(token));
        when(gatewayServiceClient.findPatientWith(any(), any())).thenReturn(Mono.just(Boolean.TRUE));
        when(patientSearchCache.asMap()).thenReturn(new ConcurrentHashMap<>());

        StepVerifier.create(patientService.findPatientWith(patientId))
                .expectErrorMatches(error -> ((ClientError) error)
                        .getError()
                        .getError()
                        .getMessage()
                        .equals("Could not connect to Gateway"))
                .verify();
    }

    @Test
    void shouldHandlePatientSearchResponse() {
        var requestId = UUID.randomUUID();
        var id = "test@ncg";
        var name = "test";
        var mockGatewayResponse = Mockito.mock(GatewayResponse.class);
        var gatewayPatientSearchResponse = Mockito.mock(PatientSearchGatewayResponse.class);
        var patientRepresentation = new PatientRepresentation(id, name);
        var patient = patientRepresentation.toPatient();
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);
        when(gatewayPatientSearchResponse.getPatient()).thenReturn(patientRepresentation);
        when(gatewayPatientSearchResponse.getResp()).thenReturn(mockGatewayResponse);
        when(gatewayPatientSearchResponse.getPatient()).thenReturn(patientRepresentation);
        when(mockGatewayResponse.getRequestId()).thenReturn(requestId.toString());

        Mono<Void> publisher = patientService.onFindPatient(gatewayPatientSearchResponse);

        StepVerifier.create(publisher)
                .expectComplete()
                .verify();
        verify(cache).put(gatewayPatientSearchResponse.getPatient().getId(), Optional.of(patient));
        verify(patientSearchCache).put(gatewayPatientSearchResponse.getResp().getRequestId(),
                        Optional.of(gatewayPatientSearchResponse));
    }
}