package in.org.projecteka.hiu.patient;

import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.ServiceCaller;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.clients.Patient;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.ConceptValidator;
import in.org.projecteka.hiu.patient.model.PatientSearchGatewayResponse;
import in.org.projecteka.hiu.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hiu.common.Constants.PATH_PATIENTS_ON_FIND;
import static in.org.projecteka.hiu.common.TestBuilders.gatewayResponse;
import static in.org.projecteka.hiu.common.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.common.TestBuilders.patientSearchGatewayResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.patient;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.patient.TestBuilders.string;
import static org.mockito.Mockito.when;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PatientControllerTest {

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @MockBean
    @Qualifier("hiuUserAuthenticator")
    private Authenticator authenticator;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    private GatewayTokenVerifier gatewayTokenVerifier;

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    CacheAdapter<String, Patient> cache;

    @MockBean
    private CacheAdapter<String, PatientSearchGatewayResponse> gatewayResponseCache;


    @MockBean
    GatewayServiceClient gatewayServiceClient;

    @Mock
    Gateway gateway;

    @SuppressWarnings("unused")
    @MockBean
    private ConceptValidator conceptValidator;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void shouldReturnUser() {
        String token = string();
        String requesterId = string();
        var caller = new Caller(requesterId, false, Role.ADMIN.toString(), true);
        var patientId = randomString();
        var patient = patient().build();
        when(cache.get(patientId)).thenReturn(just(patient));
        when(authenticator.verify(token)).thenReturn(just(caller));

        webTestClient
                .get()
                .uri("/v1/patients/{id}",patientId)
                .header("Authorization", token)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void shouldAcceptRequestOnFind() {
        String token = string();
        var requestId = UUID.randomUUID();
        var id = string();
        var gatewayResponse = gatewayResponse().requestId(requestId.toString()).build();
        var patient = patientRepresentation().id(id).build();
        var searchResponse = patientSearchGatewayResponse().patient(patient).resp(gatewayResponse).build();
        var caller = ServiceCaller.builder()
                .clientId(randomString())
                .roles(List.of(Role.GATEWAY))
                .build();

        when(gatewayTokenVerifier.verify(token)).thenReturn(just(caller));
        when(cache.put(searchResponse.getPatient().getId(), patient.toPatient())).thenReturn(empty());
        when(gatewayResponseCache.put(searchResponse.getResp().getRequestId(), searchResponse)).thenReturn(empty());

        webTestClient
                .post()
                .uri(PATH_PATIENTS_ON_FIND)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(searchResponse)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }
}