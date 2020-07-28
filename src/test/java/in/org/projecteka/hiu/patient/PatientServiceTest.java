package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static in.org.projecteka.hiu.common.TestBuilders.gatewayResponse;
import static in.org.projecteka.hiu.common.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.common.TestBuilders.patientSearchGatewayResponse;
import static in.org.projecteka.hiu.common.TestBuilders.string;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static java.lang.Boolean.TRUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

class PatientServiceTest {

    @Mock
    CacheAdapter<String, Patient> cache;

    @Mock
    CacheAdapter<String, PatientSearchGatewayResponse> patientSearchCache;

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
        when(cache.get(patientId)).thenReturn(just(patient));
        when(gateway.token()).thenReturn(just(token));
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
        when(hiuProperties.getId()).thenReturn(string());
        when(cache.get(patientId)).thenReturn(empty());
        when(gateway.token()).thenReturn(just(token));
        when(gatewayServiceClient.findPatientWith(any(), any())).thenReturn(just(TRUE));
        when(patientSearchCache.get(any())).thenReturn(empty());
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);

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
        var id = string();
        var gatewayResponse = gatewayResponse().requestId(requestId.toString()).build();
        var patient = patientRepresentation().id(id).build();
        var searchResponse = patientSearchGatewayResponse().patient(patient).resp(gatewayResponse).build();
        when(cache.put(searchResponse.getPatient().getId(), patient.toPatient())).thenReturn(empty());
        when(patientSearchCache.put(searchResponse.getResp().getRequestId(), searchResponse)).thenReturn(empty());
        var patientService = new PatientService(gatewayServiceClient,
                cache,
                hiuProperties,
                gatewayProperties,
                patientSearchCache);

        Mono<Void> publisher = patientService.onFindPatient(searchResponse);

        StepVerifier.create(publisher).expectComplete().verify();
    }
}