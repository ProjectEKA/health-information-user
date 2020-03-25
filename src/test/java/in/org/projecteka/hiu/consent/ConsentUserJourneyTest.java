package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Date;
import java.util.stream.Stream;

import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactPatient;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactReference;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentNotificationRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = ConsentUserJourneyTest.ContextInitializer.class)
public class ConsentUserJourneyTest {
    private static MockWebServer consentManagerServer = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ConsentRepository consentRepository;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;

    @MockBean
    private DataFlowRequestPublisher dataFlowRequestPublisher;

    @SuppressWarnings("unused")
    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;

    @MockBean
    private CentralRegistry centralRegistry;

    @SuppressWarnings("unused")
    @MockBean
    private JWKSet centralRegistryJWKSet;

    @MockBean
    private HealthInfoDeletionPublisher healthInfoDeletionPublisher;

    @AfterAll
    public static void tearDown() throws IOException {
        consentManagerServer.shutdown();
    }

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateConsentRequest() throws JsonProcessingException {
        String consentRequestId = "consent-request-id";
        String requesterId = "1";
        String callBackUrl = "localhost:8080";

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        var consentCreationResponse = consentCreationResponse().id(consentRequestId).build();
        var consentCreationResponseJson = new ObjectMapper().writeValueAsString(consentCreationResponse);

        consentManagerServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody(consentCreationResponseJson));

        var consentRequestDetails = consentRequestDetails().build();
        when(consentRepository.insert(consentRequestDetails.getConsent().toConsentRequest(
                consentRequestId,
                requesterId,
                callBackUrl)))
                .thenReturn(Mono.create(MonoSink::success));

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
    }

    @Test
    public void shouldThrowInsertionError() throws JsonProcessingException {
        String consentRequestId = "consent-request-id";
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
        ConsentArtefactResponse consentArtefactResponse = consentArtefactResponse()
                .status(ConsentStatus.GRANTED)
                .build();
        var consentArtefactResponseJson = new ObjectMapper().writeValueAsString(consentArtefactResponse);

        consentManagerServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(consentArtefactResponseJson));

        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        ConsentRequest consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(centralRegistry.token()).thenReturn(Mono.just("asafs"));
        when(consentRepository.get(eq(consentRequestId))).thenReturn(Mono.just(consentRequest));
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(
                anyString(),
                eq(consentArtefactResponse.getConsentDetail().getPermission().getDateRange()),
                anyString(),
                anyString())).thenReturn(Mono.empty());
        when(consentRepository.insertConsentArtefact(
                eq(consentArtefactResponse.getConsentDetail()),
                eq(consentArtefactResponse.getStatus()),
                eq(consentRequestId))).thenReturn(Mono.empty());

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
    public void shouldReturn404OnNotificationWhenConsentRequestNotFound() {
        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(null)));

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "bmNn")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    public void shouldReturn500OnNotificationWhenConsentRequestCouldNotBeFetched() {
        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.error(new Exception("Failed to fetch consent request")));

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
    public void shouldReturn500OnNotificationWhenConsentArtefactCouldNotBeInserted() throws JsonProcessingException {
        ConsentArtefactResponse consentArtefactResponse = consentArtefactResponse()
                .status(ConsentStatus.GRANTED)
                .build();
        var consentArtefactResponseJson = new ObjectMapper().writeValueAsString(consentArtefactResponse);

        consentManagerServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(consentArtefactResponseJson));

        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.GRANTED)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference().build()))
                .build();
        ConsentRequest consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(consentRequest)));
        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.insertConsentArtefact(
                eq(consentArtefactResponse.getConsentDetail()),
                eq(consentArtefactResponse.getStatus()),
                eq(consentRequestId)))
                .thenReturn(Mono.error(new Exception("Failed to insert consent artefact")));

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
    public void shouldUpdateConsentStatus() {
        String consentRequestId = "consent-request-id-1";
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        Date date = new Date();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.REVOKED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.REVOKED, date))
                .thenReturn(Mono.empty());
        when(healthInfoDeletionPublisher.broadcastHealthInfoDeletionRequest(consentArtefactReference))
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
        Date date = new Date();
        ConsentArtefactReference consentArtefactReference = consentArtefactReference().build();
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .status(ConsentStatus.REVOKED)
                .timestamp(date)
                .consentRequestId(consentRequestId)
                .consentArtefacts(singletonList(consentArtefactReference))
                .build();

        when(centralRegistry.token()).thenReturn(Mono.just(randomString()));
        when(consentRepository.updateStatus(consentArtefactReference, ConsentStatus.REVOKED, date))
                .thenReturn(Mono.error(new Exception("Failed to update consent artefact status")));
        when(healthInfoDeletionPublisher.broadcastHealthInfoDeletionRequest(consentArtefactReference))
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
                            Stream.of("hiu.consentmanager.url=" + consentManagerServer.url("")));
            values.applyTo(applicationContext);
        }
    }
}
