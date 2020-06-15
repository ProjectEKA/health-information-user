package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.GatewayCaller;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.common.CentralRegistryTokenVerifier;
import in.org.projecteka.hiu.consent.model.*;
import in.org.projecteka.hiu.consent.model.consentmanager.Permission;
import in.org.projecteka.hiu.dataflow.DataFlowDeleteListener;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.user.Role;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefact;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactPatient;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactReference;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentNotificationRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.dataflow.Utils.toDate;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = ConsentUserJourneyTest.ContextInitializer.class)
public class ConsentUserJourneyTest {
    private static final MockWebServer consentManagerServer = new MockWebServer();
    private static final MockWebServer gatewayServer = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ConsentRepository consentRepository;

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
    private CentralRegistry centralRegistry;

    @MockBean
    private CentralRegistryTokenVerifier centralRegistryTokenVerifier;

    @SuppressWarnings("unused")
    @MockBean
    private JWKSet centralRegistryJWKSet;

    @MockBean
    private HealthInformationPublisher healthInformationPublisher;

    @MockBean
    private ConceptValidator conceptValidator;

    @Captor
    ArgumentCaptor<ConsentRequest> captor;

    @AfterAll
    public static void tearDown() throws IOException {
        consentManagerServer.shutdown();
        gatewayServer.shutdown();
    }

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateConsentRequest() throws JsonProcessingException {
        var consentRequestId = "consent-request-id";
        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        var consentCreationResponse = consentCreationResponse().id(consentRequestId).build();
        var consentCreationResponseJson = new ObjectMapper().writeValueAsString(consentCreationResponse);
        when(conceptValidator.validatePurpose(anyString())).thenReturn(Mono.just(true));
        when(conceptValidator.getPurposeDescription(anyString())).thenReturn("Purpose description");

        consentManagerServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(consentCreationResponseJson));

        var consentRequestDetails = consentRequestDetails().build();
        var permission = consentRequestDetails.getConsent().getPermission();
        permission.setDataEraseAt(toDate("9999-01-15T08:47:48.373"));
        permission.setDateRange(
                DateRange.builder().from(toDate("2014-01-25T13:25:34.602"))
                        .to(toDate("2015-01-25T13:25:34.602")).build());
        when(consentRepository.insert(any())).thenReturn(Mono.create(MonoSink::success));

        webTestClient
                .post()
                .uri("/consent-requests")
                .header("Authorization", "MQ==")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id", consentRequestId);

        verify(consentRepository).insert(captor.capture());
        ConsentRequest request = captor.getValue();
        assertThat(request.getId()).isEqualTo("consent-request-id");
        assertThat(request.getConsentNotificationUrl()).isEqualTo("localhost:8080");
        assertThat(request.getPermission().getDataEraseAt()).isEqualTo("9999-01-15T08:47:48.373");
        assertThat(request.getPermission().getDateRange().getFrom()).isEqualTo("2014-01-25T13:25:34.602");
        assertThat(request.getPermission().getDateRange().getTo()).isEqualTo("2015-01-25T13:25:34.602");
    }

    @Test
    public void shouldThrowInsertionError() throws JsonProcessingException {
        var consentRequestId = "consent-request-id";
        var consentCreationResponse = consentCreationResponse().id(consentRequestId).build();
        var consentCreationResponseJson = new ObjectMapper().writeValueAsString(consentCreationResponse);
        consentManagerServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(consentCreationResponseJson));
        var consentRequestDetails = consentRequestDetails().build();

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.insert(consentRequestDetails.getConsent().toConsentRequest(consentRequestId,
                "requesterId", "localhost:8080"))).
                thenReturn(Mono.error(new Exception("Failed to insert to consent request")));

        webTestClient
                .post()
                .uri("/consent-requests")
                .header("Authorization", "MQ==")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @Disabled
    public void shouldCreateConsentArtefacts() throws JsonProcessingException {
        var dataEraseAt = "9999-01-15T08:47:48.373Z";
        var consentArtefactResponse = consentArtefactResponse()
                .status(ConsentStatus.GRANTED)
                .consentDetail(ConsentArtefact.builder()
                        .permission(Permission.builder()
                                .dataEraseAt(toDate(dataEraseAt))
                                .build())
                        .build())
                .build();
        var consentArtefactResponseJson = new ObjectMapper().writeValueAsString(consentArtefactResponse);
        consentManagerServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(consentArtefactResponseJson));
        var token = randomString();
        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        var consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(new GatewayCaller("", true, null, true)));
        when(centralRegistry.token()).thenReturn(Mono.just("asafs"));
        when(consentRepository.get(eq(consentRequestId))).thenReturn(Mono.just(consentRequest));
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(anyString(), eq(consentArtefactResponse.getConsentDetail().getPermission().getDateRange()),
                anyString(), anyString())).thenReturn(Mono.empty());
        when(consentRepository.insertConsentArtefact(
                eq(consentArtefactResponse.getConsentDetail()),
                eq(consentArtefactResponse.getStatus()),
                eq(consentRequestId))).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/consent/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    public void shouldReturn404OnNotificationWhenConsentRequestNotFound() {
        var token = randomString();
        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();

        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(null)));

        webTestClient
                .post()
                .uri("/consent/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    public void shouldReturn500OnNotificationWhenConsentRequestCouldNotBeFetched() {
        var consentRequestId = "consent-request-id-1";
        var consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        var token = randomString();

        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(new GatewayCaller("", true, null, true)));
        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.error(new Exception("Failed to fetch consent request")));

        webTestClient
                .post()
                .uri("/consent/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    public void shouldReturn500OnNotificationWhenConsentArtefactCouldNotBeInserted() throws JsonProcessingException {
        var consentArtefactResponse = consentArtefactResponse()
                .status(ConsentStatus.GRANTED)
                .build();
        var consentArtefactResponseJson = new ObjectMapper().writeValueAsString(consentArtefactResponse);
        var token = randomString();
        consentManagerServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(consentArtefactResponseJson));

        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        var consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(new GatewayCaller("", true, null, true)));
        when(centralRegistry.token()).thenReturn(Mono.just(token));
        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(consentRequest)));
        when(consentRepository.insertConsentArtefact(
                eq(consentArtefactResponse.getConsentDetail()),
                eq(consentArtefactResponse.getStatus()),
                eq(consentRequestId)))
                .thenReturn(Mono.error(new Exception("Failed to insert consent artefact")));

        webTestClient
                .post()
                .uri("/consent/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    public void shouldUpdateConsentStatus() {
        String consentRequestId = "consent-request-id-1";
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        LocalDateTime date = LocalDateTime.now();
        ConsentArtefact consentArtefact = consentArtefact().consentId(consentArtefactReference.getId()).build();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.REVOKED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        var token = randomString();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(caller));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.REVOKED, date))
                .thenReturn(Mono.empty());
        when(consentRepository.getConsent(consentArtefactReference.getId(), ConsentStatus.GRANTED))
                .thenReturn(Mono.just(consentArtefact));
        when(healthInformationPublisher.publish(consentArtefactReference))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "bmNn")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    public void shouldReturn500OnNotificationWhenConsentUpdateFails() {
        String consentRequestId = "consent-request-id-1";
        LocalDateTime date = LocalDateTime.now();
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        ConsentArtefact consentArtefact = consentArtefact().consentId(consentArtefactReference.getId()).build();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.REVOKED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        var token = randomString();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(caller));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.REVOKED, date))
                .thenReturn(Mono.error(new Exception("Failed to update consent artefact status")));
        when(consentRepository.getConsent(consentArtefactReference.getId(), ConsentStatus.GRANTED))
                .thenReturn(Mono.just(consentArtefact));
        when(healthInformationPublisher.publish(consentArtefactReference))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "bmNn")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    public void shouldUpdateConsentStatusAndBroadcastConsentDeleteOnExpiry() {
        String consentRequestId = "consent-request-id-1";
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        LocalDateTime date = LocalDateTime.now();
        ConsentArtefact consentArtefact = consentArtefact().consentId(consentArtefactReference.getId()).build();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.EXPIRED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        var token = randomString();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(new GatewayCaller("", true, (List.of(Role.values())), true)));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.EXPIRED, date))
                .thenReturn(Mono.empty());
        when(consentRepository.getConsent(consentArtefactReference.getId(), ConsentStatus.GRANTED))
                .thenReturn(Mono.just(consentArtefact));
        when(dataFlowDeletePublisher.broadcastConsentExpiry(consentArtefactReference.getId(), consentRequestId)).thenReturn(Mono.empty());
        when(healthInformationPublisher.publish(consentArtefactReference))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "bmNn")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    public void shouldReturn500OnNotificationWhenConsentExpiryUpdateFails() {
        String consentRequestId = "consent-request-id-1";
        LocalDateTime date = LocalDateTime.now();
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        ConsentArtefact consentArtefact = consentArtefact().consentId(consentArtefactReference.getId()).build();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.EXPIRED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        var token = randomString();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(caller));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.EXPIRED, date))
                .thenReturn(Mono.error(new Exception("Failed to update consent artefact status")));
        when(dataFlowDeletePublisher.broadcastConsentExpiry(consentArtefactReference.getId(), consentRequestId)).thenReturn(Mono.empty());
        when(healthInformationPublisher.publish(consentArtefactReference))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "bmNn")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    public static class ContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues values =
                    TestPropertyValues.of(
                            Stream.of("hiu.consentmanager.url=" + consentManagerServer.url(""),
                                    "hiu.gatewayservice.baseUrl=" + gatewayServer.url("")));
            values.applyTo(applicationContext);
        }
    }


    @Test
    public void shouldMakeConsentRequestToGateway() throws JsonProcessingException {
        when(conceptValidator.validatePurpose(anyString())).thenReturn(Mono.just(true));
        when(conceptValidator.getPurposeDescription(anyString())).thenReturn("Purpose description");
        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        gatewayServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        var consentRequestDetails = consentRequestDetails().build();
        consentRequestDetails.getConsent().getPatient().setId("hinapatel79@ncg");


        LocalDateTime dateEraseAt = LocalDateTime.of(LocalDate.of(2050, 01, 01), LocalTime.of(10, 30));
        consentRequestDetails.getConsent().getPermission().setDataEraseAt(dateEraseAt);
        when(consentRepository.insertConsentRequestToGateway(any())).thenReturn(Mono.create(MonoSink::success));
        webTestClient
                .post()
                .uri("/v1/hiu/consent-requests")
                .header("Authorization", "MQ==")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentRequestDetails)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    public void shouldUpdateConsentRequestWithRequestId() throws JsonProcessingException {
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
        when(consentRepository.consentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6")).thenReturn(Mono.just(ConsentStatus.POSTED));
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6",ConsentStatus.REQUESTED, "f29f0e59-8388-4698-9fe6-05db67aeac46"))
                .thenReturn(Mono.empty());
        var token = randomString();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        webTestClient
                .post()
                .uri("/v1/consent-requests/on-init")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    public void shouldUpdateConsentRequestStatusAsErrored() throws JsonProcessingException {
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
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6",ConsentStatus.ERRORED, ""))
                .thenReturn(Mono.empty());
        var token = randomString();
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        webTestClient
                .post()
                .uri("/v1/consent-requests/on-init")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    public void shouldThrowConsentRequestNotFound() throws JsonProcessingException {
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
        when(consentRepository.consentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6")).thenReturn(Mono.create(MonoSink::success));
        when(consentRepository.updateConsentRequestStatus("3fa85f64-5717-4562-b3fc-2c963f66afa6",ConsentStatus.REQUESTED, "f29f0e59-8388-4698-9fe6-05db67aeac46"))
                .thenReturn(Mono.empty());
        var token = randomString();
        var caller = GatewayCaller
                .builder()
                .username("cliendId")
                .roles(List.of(Role.values()))
                .isServiceAccount(true)
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        var errorJson = "{\"error\":{\"code\":1003,\"message\":\"Cannot find the consent request\"}}";
        webTestClient
                .post()
                .uri("/v1/consent-requests/on-init")
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
    public void shouldNotifyConsentGranted() throws JsonProcessingException, InterruptedException {
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
        var caller = GatewayCaller.builder()
                .username("abc@ncg")
                .isServiceAccount(true)
                .roles(List.of(Role.values()))
                .verified(true)
                .build();
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        var patient = Patient.builder()
                .id("heenapatel@ncg")
                .build();
        ConsentRequest consentRequest = ConsentRequest.builder().patient(patient).status(ConsentStatus.REQUESTED).build();
        when(consentRepository.get(consentRequestId)).thenReturn(Mono.just(consentRequest));
        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));

        var consent = consentArtefactResponse()
                .status(ConsentStatus.GRANTED)
                .consentDetail(
                        ConsentArtefact.builder()
                                .consentId(consentId)
                                .permission(Permission.builder().build())
                                .build())
                .build();
        gatewayServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(202));
        when(consentRepository.insertConsentArtefact(
                eq(consent.getConsentDetail()),
                eq(consent.getStatus()),
                eq(consentRequestId))).thenReturn(Mono.empty());
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(anyString(), any(),
                anyString(), anyString())).thenReturn(Mono.empty());
        webTestClient
                .post()
                .uri("/v1/consents/hiu/notify")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(notificationFromCM)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
        Thread.sleep(2000);
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
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(new GatewayCaller("", true, null, true)));

        webTestClient
                .post()
                .uri("/v1/consents/on-fetch")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(gatewayConsentArtefactResponse)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }
}
