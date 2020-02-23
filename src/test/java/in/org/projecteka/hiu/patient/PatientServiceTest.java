package in.org.projecteka.hiu.patient;

import com.google.common.cache.Cache;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.clients.PatientServiceClient;
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

    @BeforeEach
    void init() {
        initMocks(this);
    }

    @Test
    void returnPatientFromCache() {
        var patientId = randomString();
        var patient = patient().build();
        var map = new ConcurrentHashMap<String, Optional<Patient>>();
        map.put(patientId, Optional.of(patient));
        when(cache.asMap()).thenReturn(map);
        var patientService = new PatientService(client, cache);

        Mono<Patient> patientPublisher = patientService.patientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(client, never()).patientWith(patientId);
    }

    @Test
    void returnPatientFromDownstreamServiceIfCacheIsEmpty() {
        var patientId = randomString();
        var patient = patient().build();
        when(cache.asMap()).thenReturn(new ConcurrentHashMap<>());
        when(client.patientWith(patientId)).thenReturn(Mono.just(patient));
        var patientService = new PatientService(client, cache);

        Mono<Patient> patientPublisher = patientService.patientWith(patientId);

        StepVerifier.create(patientPublisher)
                .expectNext(patient)
                .verifyComplete();
        verify(client, atMostOnce()).patientWith(patientId);
    }
}