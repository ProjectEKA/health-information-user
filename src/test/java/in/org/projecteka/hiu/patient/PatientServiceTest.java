package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
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
        verify(gatewayServiceClient, never()).findPatientWith(any(), any(), any());
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
        when(gatewayServiceClient.findPatientWith(any(), any(), any())).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(patientService.findPatientWith(patientId))
                .expectErrorMatches(error -> ((ClientError) error)
                        .getError()
                        .getError()
                        .getMessage()
                        .equals("Didn't receive any result from Gateway"))
                .verify();

    }
}