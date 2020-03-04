package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
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
        var patientService = new PatientService(client, cache, centralRegistry);

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
        var patient = patient().build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(client.patientWith(patientId, token)).thenReturn(Mono.just(patient));
        var patientService = new PatientService(client, cache, centralRegistry);

        Mono<Patient> patientPublisher = patientService.patientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(client, atMostOnce()).patientWith(patientId, token);
    }
}