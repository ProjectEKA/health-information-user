package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.consent.model.ConsentArtefactResponse;
import in.org.projecteka.hiu.consent.model.ConsentNotificationRequest;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.stream.Stream;

import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactPatient;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactReference;
import static in.org.projecteka.hiu.consent.TestBuilders.consentArtefactResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentNotificationRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequest;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRequestDetails;
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
    
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @MockBean
    private DestinationsConfig destinationsConfig;

    @MockBean
    private DataFlowRequestPublisher dataFlowRequestPublisher;


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

        when(consentRepository.insert(consentRequestDetails.getConsent().toConsentRequest(consentRequestId, "requesterId", "localhost:8080"))).
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
                .consentRequestId(consentRequestId)
                .consents(singletonList(consentArtefactReference().status(ConsentStatus.GRANTED).build()))
                .build();
        ConsentRequest consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(consentRequest)));
        when(dataFlowRequestPublisher.broadcastDataFlowRequest(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(consentRepository.insertConsentArtefact(
                eq(consentArtefactResponse.getConsentDetail()),
                eq(consentArtefactResponse.getStatus()),
                eq(consentRequestId)))
                .thenReturn(Mono.create(MonoSink::success));

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
                .consentRequestId(consentRequestId)
                .consents(singletonList(consentArtefactReference().build()))
                .build();

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
                .consents(singletonList(consentArtefactReference().build()))
                .build();

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
                .consentRequestId(consentRequestId)
                .consents(singletonList(consentArtefactReference().status(ConsentStatus.GRANTED).build()))
                .build();
        ConsentRequest consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(consentRequest)));

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
    public void shouldReturn401WhenConsentManagerIsInvalid() {
        String consentRequestId = "consent-request-id-1";
        ConsentNotificationRequest consentNotificationRequest = consentNotificationRequest()
                .consentRequestId(consentRequestId)
                .consents(singletonList(consentArtefactReference().build()))
                .build();
        ConsentRequest consentRequest = consentRequest()
                .id(consentRequestId)
                .patient(consentArtefactPatient().id("5@ncg").build())
                .build();

        when(consentRepository.get(eq(consentRequestId)))
                .thenReturn(Mono.create(consentRequestMonoSink -> consentRequestMonoSink.success(consentRequest)));

        webTestClient
                .post()
                .uri("/consent/notification/")
                .header("Authorization", "abcd")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(consentNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized();
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
