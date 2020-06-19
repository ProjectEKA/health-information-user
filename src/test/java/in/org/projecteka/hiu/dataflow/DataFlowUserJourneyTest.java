package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.ServiceCaller;
import in.org.projecteka.hiu.common.CentralRegistryTokenVerifier;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import in.org.projecteka.hiu.user.Role;
import okhttp3.mockwebserver.MockWebServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.dataflow.TestBuilders.*;
import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequestResult;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
public class DataFlowUserJourneyTest {
    private static final MockWebServer dataFlowServer = new MockWebServer();
    @MockBean
    LocalDataStore localDataStore;
    @Autowired
    private WebTestClient webTestClient;
    @MockBean
    private DataFlowRepository dataFlowRepository;
    @MockBean
    private HealthInformationRepository healthInformationRepository;
    @MockBean
    private ConsentRepository consentRepository;
    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;
    @SuppressWarnings("unused")
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;
    @MockBean
    private DataFlowDeleteListener dataFlowDeleteListener;
    @MockBean
    private DataAvailabilityPublisher dataAvailabilityPublisher;
    @SuppressWarnings("unused")
    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;
    @MockBean
    private CentralRegistryTokenVerifier centralRegistryTokenVerifier;
    @SuppressWarnings("unused")
    @MockBean
    private JWKSet centralRegistryJWKSet;

    @AfterAll
    public static void tearDown() throws IOException {
        dataFlowServer.shutdown();
    }

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotifyDataFlowResponse() {
        var entry = entry().build();
        entry.setLink(null);
        entry.setContent("Some Dummy Content XYZ 1");
        List<Entry> entries = new ArrayList<>();
        entries.add(entry);
        var transactionId = "transactionId";
        var keyMaterial = keyMaterial().build();
        var dataNotificationRequest =
                DataNotificationRequest.builder()
                        .transactionId(transactionId)
                        .entries(entries)
                        .keyMaterial(keyMaterial)
                        .build();
        Map<String, Object> flowRequestMap = new HashMap<>();
        var token = randomString();
        flowRequestMap.put("consentRequestId", "consentRequestId");
        flowRequestMap.put("consentExpiryDate", LocalDateTime.parse("9999-04-15T16:55:00"));
        var caller = ServiceCaller.builder()
                .clientId("abc@ncg")
                .roles(List.of(Role.GATEWAY))
                .build();

        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        when(dataFlowRepository.insertDataPartAvailability(transactionId, 1, HealthInfoStatus.RECEIVED))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.retrieveDataFlowRequest(transactionId)).thenReturn(Mono.just(flowRequestMap));
        when(dataAvailabilityPublisher.broadcastDataAvailability(any())).thenReturn(Mono.empty());
        when(localDataStore.serializeDataToFile(any(), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/data/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    public void shouldFetchHealthInformation() {
        var consentRequestId = "consentRequestId";
        var consentId = "consentId";
        var transactionId = "transactionId";
        var hipId = "10000005";
        var hipName = "Max health care";
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "1");
        consentDetailsMap.put("status", "GRANTED");
        consentDetailsMap.put("consentExpiryDate", "9999-01-15T08:47:48");
        consentDetails.add(consentDetailsMap);
        Map<String, Object> healthInfo = new HashMap<>();
        String content = "Some dummy content";
        healthInfo.put("data", content);
        healthInfo.put("status", EntryStatus.SUCCEEDED.toString());
        DataEntry dataEntry =
                DataEntry.builder().hipId(hipId).hipName(hipName).data(content).status(EntryStatus.SUCCEEDED).build();
        List<DataEntry> dataEntries = new ArrayList<>();
        dataEntries.add(dataEntry);
        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));
        when(dataFlowRepository.getTransactionId(consentId)).thenReturn(Mono.just(transactionId));
        when(healthInformationRepository.getHealthInformation(transactionId)).thenReturn(Flux.just(healthInfo));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/health-information/fetch/consentRequestId")
                        .queryParam("limit", "20").build())
                .header("Authorization", "MQ==")
                .exchange()
                .expectStatus().isOk()
                .expectBody(HealthInformation.class)
                .value(HealthInformation::getLimit, Matchers.is(20))
                .value(HealthInformation::getOffset, Matchers.is(0))
                .value(HealthInformation::getSize, Matchers.is(1))
                .value(HealthInformation::getEntries, Matchers.is(dataEntries));
    }

    @Test
    public void shouldNotFetchHealthInformationForExpiredConsent() throws JsonProcessingException {
        var consentRequestId = "consentRequestId";
        var consentId = "consentId";
        var transactionId = "transactionId";
        var hipId = "10000005";
        var hipName = "Max health care";
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "1");
        consentDetailsMap.put("status", "GRANTED");
        consentDetailsMap.put("consentExpiryDate", "2019-01-15T08:47:48");
        consentDetails.add(consentDetailsMap);
        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.CONSENT_ARTEFACT_NOT_FOUND,
                "Consent artefact expired"));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);

        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));
        when(dataFlowRepository.getTransactionId(consentId)).thenReturn(Mono.just(transactionId));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/health-information/fetch/consentRequestId")
                        .queryParam("limit", "20").build())
                .header("Authorization", "MQ==")
                .exchange()
                .expectStatus().is4xxClientError()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    public void shouldThrowUnauthorized() throws JsonProcessingException {
        String consentRequestId = "consentRequestId";
        String consentId = "consentId";
        String hipId = "10000005";
        String hipName = "Max health care";
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "2");
        consentDetails.add(consentDetailsMap);

        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.UNAUTHORIZED_REQUESTER,
                "Requester is not authorized to perform this action"));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);

        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/health-information/fetch/consentRequestId")
                        .queryParam("limit", "20").build())
                .header("Authorization", "MQ==")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    public void shouldThrowBadRequestErrorIfLinkAndContentAreEmpty() throws JsonProcessingException {
        Entry entry = new Entry();
        entry.setLink(null);
        List<Entry> entries = new ArrayList<>();
        entries.add(entry);
        String transactionId = "transactionId";
        DataNotificationRequest dataNotificationRequest =
                DataNotificationRequest.builder().transactionId(transactionId).entries(entries).build();
        var token = randomString();
        var caller = ServiceCaller.builder()
                .clientId("abc@ncg")
                .roles(List.of(Role.GATEWAY))
                .build();
        when(centralRegistryTokenVerifier.verify(token)).thenReturn(Mono.just(caller));
        when(dataFlowRepository.insertDataPartAvailability(transactionId, 1, HealthInfoStatus.RECEIVED))
                .thenReturn(Mono.empty());
        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.INVALID_DATA_FLOW_ENTRY,
                "Entry must either have content or provide a link."));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);

        webTestClient
                .post()
                .uri("/data/notification")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldUpdateDataFlowRequest() {
        var token = string();
        var dataFlowRequestResult = dataFlowRequestResult().error(null).build();
        var transactionId = dataFlowRequestResult.getHiRequest().getTransactionId().toString();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(new Caller("", true, "", true)));
        when(dataFlowRepository.updateDataRequest(
                transactionId,
                dataFlowRequestResult.getHiRequest().getSessionStatus(),
                dataFlowRequestResult.getResp().getRequestId()
        )).thenReturn(Mono.empty());
        when(dataFlowRepository.addKeys(eq(transactionId), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/v1/health-information/hiu/on-request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequestResult)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldNotUpdateDataFlowRequest() {
        var token = string();
        var dataFlowRequestResult = dataFlowRequestResult().hiRequest(null).build();
        when(centralRegistryTokenVerifier.verify(token))
                .thenReturn(Mono.just(new Caller("", true, "", true)));

        webTestClient
                .post()
                .uri("/v1/health-information/hiu/on-request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequestResult)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }
}
