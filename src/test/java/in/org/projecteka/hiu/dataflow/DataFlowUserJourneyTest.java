package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.ServiceCaller;
import in.org.projecteka.hiu.clients.GatewayServiceClient;
import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import static in.org.projecteka.hiu.common.Constants.PATH_HEALTH_INFORMATION_HIU_ON_REQUEST;
import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequestResult;
import static in.org.projecteka.hiu.dataflow.TestBuilders.entry;
import static in.org.projecteka.hiu.dataflow.TestBuilders.keyMaterial;
import static in.org.projecteka.hiu.dataflow.TestBuilders.string;
import static in.org.projecteka.hiu.user.Role.GATEWAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
class DataFlowUserJourneyTest {
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
    private GatewayServiceClient gatewayServiceClient;
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
    @Qualifier("hiuUserAuthenticator")
    private Authenticator authenticator;

    @MockBean
    private GatewayTokenVerifier gatewayTokenVerifier;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    @Qualifier("userAuthenticator")
    private Authenticator userAuthenticator;

    @AfterAll
    static void tearDown() throws IOException {
        dataFlowServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void shouldNotifyDataFlowResponse() {
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
        flowRequestMap.put("consentRequestId", "consentRequestId");
        flowRequestMap.put("consentExpiryDate", LocalDateTime.parse("9999-04-15T16:55:00"));
        when(dataFlowRepository.insertDataPartAvailability(transactionId, 1, HealthInfoStatus.RECEIVED))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.retrieveDataFlowRequest(transactionId)).thenReturn(Mono.just(flowRequestMap));
        when(dataAvailabilityPublisher.broadcastDataAvailability(any())).thenReturn(Mono.empty());
        when(localDataStore.serializeDataToFile(any(), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri(Constants.PATH_DATA_TRANSFER)
                .contentType(APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldFetchHealthInformation() {
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
        consentDetailsMap.put("requester", "testUser");
        consentDetailsMap.put("status", "GRANTED");
        consentDetailsMap.put("consentExpiryDate", "9999-01-15T08:47:48");
        consentDetails.add(consentDetailsMap);
        var token = randomString();
        var caller = new Caller("testUser", false, Role.ADMIN.toString(), true);
        when(authenticator.verify(token)).thenReturn(Mono.just(caller));
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
                .header("Authorization", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(HealthInformation.class)
                .value(HealthInformation::getLimit, Matchers.is(20))
                .value(HealthInformation::getOffset, Matchers.is(0))
                .value(HealthInformation::getSize, Matchers.is(1))
                .value(HealthInformation::getEntries, Matchers.is(dataEntries));
    }

    @Test
    void shouldNotFetchHealthInformationForExpiredConsent() throws JsonProcessingException {
        var consentRequestId = "consentRequestId";
        var consentId = "consentId";
        var transactionId = "transactionId";
        var hipId = "10000005";
        var hipName = "Max health care";
        var token = randomString();
        var caller = new Caller("testUser", false, Role.ADMIN.toString(), true);
        when(authenticator.verify(token)).thenReturn(Mono.just(caller));
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "testUser");
        consentDetailsMap.put("status", "GRANTED");
        consentDetailsMap.put("consentExpiryDate", "2019-01-15T08:47:48");
        consentDetails.add(consentDetailsMap);
        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.CONSENT_ARTEFACT_NOT_FOUND,
                "Consent artefact expired"));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);
        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));
        when(dataFlowRepository.getTransactionId(consentId)).thenReturn(Mono.just(transactionId));
        when(gatewayServiceClient.sendConsentOnNotify(any(), any())).thenReturn(Mono.empty());
        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/health-information/fetch/consentRequestId")
                        .queryParam("limit", "20").build())
                .header("Authorization", token)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldThrowUnauthorized() throws JsonProcessingException {
        String consentRequestId = "consentRequestId";
        String consentId = "consentId";
        String hipId = "10000005";
        String hipName = "Max health care";
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "tempUser");
        consentDetails.add(consentDetailsMap);
        var token = randomString();
        var caller = new Caller("testUser", false, Role.ADMIN.toString(), true);
        when(authenticator.verify(token)).thenReturn(Mono.just(caller));
        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.UNAUTHORIZED_REQUESTER,
                "Requester is not authorized to perform this action"));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);
        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));

        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/health-information/fetch/consentRequestId")
                        .queryParam("limit", "20").build())
                .header("Authorization", token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldThrowBadRequestErrorIfLinkAndContentAreEmpty() throws JsonProcessingException {
        String transactionId = "transactionId";
        var dataNotificationRequest =
                DataNotificationRequest.builder().transactionId(transactionId).entries(List.of(new Entry())).build();
        when(dataFlowRepository.insertDataPartAvailability(transactionId, 1, HealthInfoStatus.RECEIVED))
                .thenReturn(Mono.empty());
        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.INVALID_DATA_FLOW_ENTRY,
                "Entry must either have content or provide a link."));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);

        webTestClient
                .post()
                .uri(Constants.PATH_DATA_TRANSFER)
                .contentType(APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldUpdateDataFlowRequest() {
        var token = string();
        var clientId = string();
        var dataFlowRequestResult = dataFlowRequestResult().error(null).build();
        var transactionId = dataFlowRequestResult.getHiRequest().getTransactionId().toString();
        when(gatewayTokenVerifier.verify(token))
                .thenReturn(Mono.just(new ServiceCaller(clientId, (List.of(GATEWAY)))));
        when(dataFlowRepository.updateDataRequest(
                transactionId,
                dataFlowRequestResult.getHiRequest().getSessionStatus(),
                dataFlowRequestResult.getResp().getRequestId()
        )).thenReturn(Mono.empty());
        when(dataFlowRepository.addKeys(eq(transactionId), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri(PATH_HEALTH_INFORMATION_HIU_ON_REQUEST)
                .header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .bodyValue(dataFlowRequestResult)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    @Test
    void shouldNotUpdateDataFlowRequest() {
        var token = string();
        var clientId = string();
        var dataFlowRequestResult = dataFlowRequestResult().hiRequest(null).build();
        when(gatewayTokenVerifier.verify(token))
                .thenReturn(Mono.just(new ServiceCaller(clientId, (List.of(GATEWAY)))));

        webTestClient
                .post()
                .uri(PATH_HEALTH_INFORMATION_HIU_ON_REQUEST)
                .header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .bodyValue(dataFlowRequestResult)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }
}
