package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
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
import static in.org.projecteka.hiu.consent.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class PatientServiceTest {

    @Mock
    PatientServiceClient client;

    @Mock
    Cache<String, Optional<Patient>> cache;

    @Mock
    CentralRegistry centralRegistry;

    @Mock
    HiuProperties hiuProperties;

    @Mock
    GatewayServiceProperties gatewayServiceProperties;

    @Mock
    GatewayServiceClient gatewayServiceClient;


    @BeforeEach
    void init() {
        initMocks(this);
    }

    @Test
    void returnPatientFromCache() {
        var patientId = randomString();
        var token = randomString();
        var patient = patient().build();
        var map = new ConcurrentHashMap<String, Optional<Patient>>();
        map.put(patientId, Optional.of(patient));
        when(cache.asMap()).thenReturn(map);
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        var patientService = new PatientService(client, gatewayServiceClient, cache, centralRegistry, hiuProperties, gatewayServiceProperties);

        Mono<Patient> patientPublisher = patientService.patientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(client, never()).patientWith(patientId, token);
    }

    @Test
    void returnPatientFromDownstreamServiceIfCacheIsEmpty() {
        var patientId = randomString();
        var token = randomString();
        var patientRep = patientRepresentation().name("arun").build();
        var patient =
                patient().identifier(patientRep.getIdentifier()).firstName(patientRep.getName()).lastName(null).build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(client.patientWith(patientId, token)).thenReturn(Mono.just(patientRep));
        var patientService = new PatientService(client, gatewayServiceClient, cache, centralRegistry, hiuProperties, gatewayServiceProperties);

        Mono<Patient> patientPublisher = patientService.patientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(client, atMostOnce()).patientWith(patientId, token);
    }

    @Test
    void returnPatientFromCacheForFindPatient() {
        var patientId = randomString();
        var token = randomString();
        var patient = patient().build();
        var map = new ConcurrentHashMap<String, Optional<Patient>>();
        map.put(patientId, Optional.of(patient));
        when(cache.asMap()).thenReturn(map);
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        var patientService = new PatientService(client, gatewayServiceClient, cache, centralRegistry, hiuProperties, gatewayServiceProperties);

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
        var patient = patient().build();
        var map = new ConcurrentHashMap<String, Optional<Patient>>();
        map.put(patientId, Optional.of(patient));

        var patientService = new PatientService(client, gatewayServiceClient, cache, centralRegistry, hiuProperties, gatewayServiceProperties);

        when(hiuProperties.getId()).thenReturn("10000005");
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(gatewayServiceClient.findPatientWith(any(), any())).thenReturn(Mono.just(Boolean.TRUE));

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
        var patient = PatientRepresentation.toPatient(patientRepresentation);


        var patientService = new PatientService(client, gatewayServiceClient, cache, centralRegistry, hiuProperties, gatewayServiceProperties);

        when(gatewayPatientSearchResponse.getPatient()).thenReturn(patientRepresentation);
        when(gatewayPatientSearchResponse.getResp()).thenReturn(mockGatewayResponse);
        when(gatewayPatientSearchResponse.getPatient()).thenReturn(patientRepresentation);
        when(mockGatewayResponse.getRequestId()).thenReturn(requestId.toString());

        StepVerifier.create(patientService.onFindPatient(gatewayPatientSearchResponse))
                .expectComplete().verify();
        verify(cache).put(eq(gatewayPatientSearchResponse.getResp().getRequestId()),eq(Optional.of(patient)));
    }
}