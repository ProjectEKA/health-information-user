package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.ServiceCaller;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.consent.model.Patient;
import in.org.projecteka.hiu.consent.model.PatientConsentRequest;
import in.org.projecteka.hiu.consent.model.consentmanager.Permission;
import in.org.projecteka.hiu.dataflow.DataFlowDeleteListener;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataflow.HealthInfoManager;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.user.Role;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static in.org.projecteka.hiu.common.Constants.APP_PATH_HIU_CONSENT_REQUESTS;
import static in.org.projecteka.hiu.common.Constants.APP_PATH_PATIENT_CONSENT_REQUEST;
import static in.org.projecteka.hiu.common.Constants.PATH_CONSENT_REQUESTS_ON_INIT;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.GRANTED;
import static in.org.projecteka.hiu.consent.model.ConsentStatus.REQUESTED;
import static java.lang.Thread.sleep;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "600000")
@ContextConfiguration(initializers = ConsentUserJourneyTest.ContextInitializer.class)
class ConsentUserJourneyTest {
    private static final MockWebServer consentManagerServer = new MockWebServer();
    private static final MockWebServer gatewayServer = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    @Qualifier("patientRequestCache")
    private CacheAdapter<String, String> patientRequestCache;

    @MockBean
    @Qualifier("gatewayResponseCache")
    private CacheAdapter<String, String> gatewayResponseCache;

    @MockBean
    private ConsentRepository consentRepository;

    @MockBean
    private PatientConsentRepository patientConsentRepository;
    @SuppressWarnings("unused")
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowDeleteListener dataFlowDeleteListener;

    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;

    @MockBean
    private DataFlowRequestPublisher dataFlowRequestPublisher;

    @MockBean
    private DataFlowDeletePublisher dataFlowDeletePublisher;

    @SuppressWarnings("unused")
    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;

    @MockBean
    private Gateway gateway;

    @MockBean
    private GatewayTokenVerifier gatewayTokenVerifier;

    @MockBean
    @Qualifier("hiuUserAuthenticator")
    private Authenticator authenticator;

    @MockBean
    @Qualifier("userAuthenticator")
    private Authenticator cmPatientAuthenticator;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    private HealthInformationPublisher healthInformationPublisher;

    @MockBean
    private ConceptValidator conceptValidator;

    @MockBean
    HealthInfoManager healthInfoManager;


    @AfterAll
    static void tearDown() throws IOException {
        consentManagerServer.shutdown();
        gatewayServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    static class ContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
            TestPropertyValues values =
                    TestPropertyValues.of(
                            Stream.of("hiu.consentmanager.url=" + consentManagerServer.url(""),
                                    "hiu.gatewayservice.baseUrl=" + gatewayServer.url("")));
            values.applyTo(applicationContext);
        }
    }

    @Test
    void shouldMakeConsentRequestToGateway() {
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(conceptValidator.getPurposeDescription(anyString())).thenReturn("Purpose description");
        when(gateway.token()).thenReturn(just(randomString()));
        gatewayServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        var consentRequestDetails = consentRequestDetails().build();
        consentRequestDetails.getConsent().getPatient().setId("hinapatel79@ncg");
        var token = randomString();
        var caller = new Caller("testUser", false, Role.ADMIN.toString(), true);
        when(authenticator.verify(token)).thenReturn(just(caller));
        var dateEraseAt = LocalDateTime.of(LocalDate.of(2050, 1, 1), LocalTime.of(10, 30));
        consentRequestDetails.getConsent().getPermission().setDataEraseAt(dateEraseAt);
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));

        webTestClient
                .post()
                .uri(APP_PATH_HIU_CONSENT_REQUESTS)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldUpdateConsentRequestWithRequestId() {
        String responseFromCM = "{\n" +
                "  \"requestId\": \"5f7a535d-a3fd-416b-b069-c97d021fbacd\",\n" +
                "  \"timestamp\": \"2020-06-01T12:54:32.862Z\",\n" +
                "  \"consentRequest\": {\n" +
                "    \"id\": \"f29f0e59-8388-4698-9fe6-05db67aeac46\"\n" +
                "  },\n" +
                "  \"resp\": {\n" +
                "    \"requestId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"\n" +
                "  }\n" +
                "}";
        when(patientRequestCache.get("3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .thenReturn(just("3fa85f64-5717-4562-b3fc-2c963f66afa7"));
        when(consentRepository.consentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .thenReturn(just(ConsentStatus.POSTED));
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6",
                REQUESTED,
                "f29f0e59-8388-4698-9fe6-05db67aeac46"))
                .thenReturn(empty());
        when(patientConsentRepository.updatePatientConsentRequest(any(), any(), any()))
                .thenReturn(empty());
        var token = randomString();
        var caller = ServiceCaller.builder()
                .clientId("abc@ncg")
                .roles(List.of(Role.values()))
                .build();
        when(gatewayTokenVerifier.verify(token)).thenReturn(just(caller));

        webTestClient
                .post()
                .uri(PATH_CONSENT_REQUESTS_ON_INIT)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldUpdateConsentRequestStatusAsErrored() {
        String responseFromCM = "{\n" +
                "  \"requestId\": \"5f7a535d-a3fd-416b-b069-c97d021fbacd\",\n" +
                "  \"timestamp\": \"2020-06-01T12:54:32.862Z\",\n" +
                "  \"error\": {\n" +
                "    \"code\": 1000,\n" +
                "    \"message\": \"string\"\n" +
                "  }," +
                "  \"resp\": {\n" +
                "    \"requestId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"\n" +
                "  }\n" +
                "}";
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6", ConsentStatus.ERRORED, ""))
                .thenReturn(empty());
        var token = randomString();
        var caller = ServiceCaller.builder()
                .clientId("abc@ncg")
                .roles(List.of(Role.values()))
                .build();
        when(gatewayTokenVerifier.verify(token)).thenReturn(just(caller));

        webTestClient
                .post()
                .uri(PATH_CONSENT_REQUESTS_ON_INIT)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldThrowConsentRequestNotFound() {
        when(patientRequestCache.get("3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .thenReturn(just("3fa85f64-5717-4562-b3fc-2c963f66afa7"));
        String responseFromCM = "{\n" +
                "  \"requestId\": \"5f7a535d-a3fd-416b-b069-c97d021fbacd\",\n" +
                "  \"timestamp\": \"2020-06-01T12:54:32.862Z\",\n" +
                "  \"consentRequest\": {\n" +
                "    \"id\": \"f29f0e59-8388-4698-9fe6-05db67aeac46\"\n" +
                "  },\n" +
                "  \"resp\": {\n" +
                "    \"requestId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"\n" +
                "  }\n" +
                "}";
        when(consentRepository.consentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .thenReturn(Mono.create(MonoSink::success));
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6",
                REQUESTED,
                "f29f0e59-8388-4698-9fe6-05db67aeac46"))
                .thenReturn(empty());
        when(patientConsentRepository.updatePatientConsentRequest(any(), any(), any()))
                .thenReturn(empty());
        var token = randomString();
        var caller = ServiceCaller
                .builder()
                .clientId("cliendId")
                .roles(List.of(Role.values()))
                .build();
        when(gatewayTokenVerifier.verify(token)).thenReturn(just(caller));
        var errorJson = "{\"error\":{\"code\":4404,\"message\":\"Cannot find the consent request\"}}";

        webTestClient
                .post()
                .uri(PATH_CONSENT_REQUESTS_ON_INIT)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .json(errorJson);
    }

    @Test
    void shouldNotifyConsentGranted() throws InterruptedException {
        String consentRequestId = "46ac0879-7f6d-4a5b-bc03-3f36782937a5";
        String consentId = "ae00bb0c-8e29-4fe3-a09b-4c976757d933";
        String notificationFromCM = "{\n" +
                "  \"requestId\": \"e815dc70-0b18-4f7c-9a03-17aed83d5ac2\",\n" +
                "  \"timestamp\": \"2020-06-04T11:01:11.045Z\",\n" +
                "  \"notification\": {\n" +
                "    \"consentRequestId\": \"" + consentRequestId + "\",\n" +
                "    \"status\": \"GRANTED\",\n" +
                "    \"consentArtefacts\": [\n" +
                "      {\n" +
                "        \"id\": \"" + consentId + "\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        var token = randomString();
        var caller = ServiceCaller.builder().clientId("abc@ncg").roles(List.of(Role.values())).build();
        when(gatewayTokenVerifier.verify(token)).thenReturn(just(caller));
        var patient = Patient.builder().id("heenapatel@ncg").build();
        when(gatewayResponseCache.put(any(), any())).thenReturn(empty());
        var consentRequest = ConsentRequest.builder().patient(patient).status(REQUESTED).build();
        when(consentRepository.get(consentRequestId)).thenReturn(just(consentRequest));
        when(gateway.token()).thenReturn(just(randomString()));
        var consent = consentArtefactResponse()
                .status(GRANTED)
                .consentDetail(ConsentArtefact.builder()
                        .consentId(consentId)
                        .permission(Permission.builder().build())
                        .build())
                .build();
        gatewayServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        when(consentRepository.updateConsentRequestStatus(GRANTED, consentRequestId)).thenReturn(empty());
        when(consentRepository.insertConsentArtefact(
                eq(consent.getConsentDetail()),
                eq(consent.getStatus()),
                eq(consentRequestId))).thenReturn(empty());
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(anyString(), any(),
                anyString(), anyString())).thenReturn(empty());

        webTestClient
                .post()
                .uri(Constants.PATH_CONSENTS_HIU_NOTIFY)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notificationFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
        sleep(2000);
    }

    @Ignore
    void shouldInsertConsentArtefact() {
        String gatewayConsentArtefactResponse = "{\n" +
                "  \"requestId\": \"5f7a535d-a3fd-416b-b069-c97d021fbacd\",\n" +
                "  \"timestamp\": \"2020-06-08T05:19:24.739Z\",\n" +
                "  \"consent\": {\n" +
                "    \"status\": \"GRANTED\",\n" +
                "    \"consentDetail\": {\n" +
                "      \"schemaVersion\": \"string\",\n" +
                "      \"consentId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\n" +
                "      \"createdAt\": \"2020-06-08T05:19:24.739Z\",\n" +
                "      \"patient\": {\n" +
                "        \"id\": \"batman@ncg\"\n" +
                "      },\n" +
                "      \"careContexts\": [\n" +
                "        {\n" +
                "          \"patientReference\": \"batman@tmh\",\n" +
                "          \"careContextReference\": \"Episode1\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"purpose\": {\n" +
                "        \"text\": \"string\",\n" +
                "        \"code\": \"string\",\n" +
                "        \"refUri\": \"string\"\n" +
                "      },\n" +
                "      \"hip\": {\n" +
                "        \"id\": \"string\"\n" +
                "      },\n" +
                "      \"hiu\": {\n" +
                "        \"id\": \"string\"\n" +
                "      },\n" +
                "      \"consentManager\": {\n" +
                "        \"id\": \"string\"\n" +
                "      },\n" +
                "      \"requester\": {\n" +
                "        \"name\": \"Dr. Manju\",\n" +
                "        \"identifier\": {\n" +
                "          \"type\": \"REGNO\",\n" +
                "          \"value\": \"MH1001\",\n" +
                "          \"system\": \"https://www.mciindia.org\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"hiTypes\": [\n" +
                "        \"Condition\"\n" +
                "      ],\n" +
                "      \"permission\": {\n" +
                "        \"accessMode\": \"VIEW\",\n" +
                "        \"dateRange\": {\n" +
                "          \"from\": \"2020-06-08T05:19:24.739Z\",\n" +
                "          \"to\": \"2020-06-08T05:19:24.739Z\"\n" +
                "        },\n" +
                "        \"dataEraseAt\": \"2020-06-08T05:19:24.739Z\",\n" +
                "        \"frequency\": {\n" +
                "          \"unit\": \"HOUR\",\n" +
                "          \"value\": 0,\n" +
                "          \"repeats\": 0\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"signature\": \"Signature of CM as defined in W3C standards; Base64 encoded\"\n" +
                "  },\n" +
                "  \"resp\": {\n" +
                "    \"requestId\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\"\n" +
                "  }\n" +
                "}";
        var token = randomString();
        when(gatewayTokenVerifier.verify(token)).thenReturn(just(new ServiceCaller("", null)));

        webTestClient
                .post()
                .uri(Constants.PATH_CONSENTS_ON_FETCH)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(gatewayConsentArtefactResponse)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldMakeAConsentRequestForTheFirstTime() {
        String requesterId = "hinapatel79@ncg";
        String hipId = "100005";
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(conceptValidator.getPurposeDescription(anyString())).thenReturn("Purpose description");
        when(gateway.token()).thenReturn(just(randomString()));
        gatewayServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        var consentRequestDetails = new PatientConsentRequest(List.of(hipId), false);
        var token = randomString();
        var caller = new Caller(requesterId, false, Role.ADMIN.toString(), true);
        when(cmPatientAuthenticator.verify(token)).thenReturn(just(caller));
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));
        when(patientConsentRepository.getLatestDataRequestsForPatient(eq(requesterId), any())).thenReturn(Mono.empty());
        webTestClient
                .post()
                .uri(APP_PATH_PATIENT_CONSENT_REQUEST)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldReturnEmptyResponseForAConsentRequestWithEmptyHipId() throws JsonProcessingException {
        String requesterId = "hinapatel79@ncg";
        String hipId = "";
        when(conceptValidator.validatePurpose(anyString())).thenReturn(just(true));
        when(conceptValidator.getPurposeDescription(anyString())).thenReturn("Purpose description");
        when(gateway.token()).thenReturn(just(randomString()));
        gatewayServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        var consentRequestDetails = new PatientConsentRequest(List.of(hipId), false);
        var token = randomString();
        var caller = new Caller(requesterId, false, Role.ADMIN.toString(), true);
        when(cmPatientAuthenticator.verify(token)).thenReturn(just(caller));
        when(healthInfoManager.fetchHealthInformationStatus(any())).thenReturn(Flux.empty());

        webTestClient
                .post()
                .uri(APP_PATH_PATIENT_CONSENT_REQUEST)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody()
                .json("{}");
    }
}
