package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import in.org.projecteka.hiu.dataflow.model.Status;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import okhttp3.mockwebserver.MockWebServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.dataflow.TestBuilders.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
public class DataFlowUserJourneyTest {
    private static MockWebServer dataFlowServer = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DataFlowRepository dataFlowRepository;

    @MockBean
    private HealthInformationRepository healthInformationRepository;

    @MockBean
    private ConsentRepository consentRepository;

    @MockBean
    private DestinationsConfig destinationsConfig;

    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @MockBean
    private DataAvailabilityPublisher dataAvailabilityPublisher;

    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;

    @MockBean
    LocalDataStore localDataStore;

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
        Entry entry = entry().build();
        entry.setLink(null);
        entry.setContent("Some Dummy Content XYZ 1");
        List<Entry> entries = new ArrayList<>();
        entries.add(entry);
        String transactionId = "transactionId";
        DataNotificationRequest dataNotificationRequest =
                DataNotificationRequest.builder().transactionId(transactionId).entries(entries).build();

        when(dataFlowRepository.insertDataPartAvailability(transactionId, 1)).thenReturn(Mono.empty());
        when(dataFlowRepository.retrieveDataFlowRequest(transactionId)).thenReturn(Mono.just(new DataFlowRequest()));
        when(dataAvailabilityPublisher.broadcastDataAvailability(any())).thenReturn(Mono.empty());
        when(localDataStore.serializeDataToFile(any(), any())).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/data/notification")
                .header("Authorization", "R2FuZXNoQG5jZw==")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    public void shouldFetchHealthInformation() {
        String consentRequestId = "consentRequestId";
        String consentId = "consentId";
        String transactionId = "transactionId";
        String hipId = "10000005";
        String hipName = "Max health care";
        List<Map<String, String>> consentDetails = new ArrayList<>();
        Map<String, String> consentDetailsMap = new HashMap<>();
        consentDetailsMap.put("consentId", consentId);
        consentDetailsMap.put("hipId", hipId);
        consentDetailsMap.put("hipName", hipName);
        consentDetailsMap.put("requester", "1");
        consentDetails.add(consentDetailsMap);
        Entry entry = entry().build();
        DataEntry dataEntry =
                DataEntry.builder().hipId(hipId).hipName(hipName).status(Status.COMPLETED).entry(entry).build();
        List<DataEntry> dataEntries = new ArrayList<>();
        dataEntries.add(dataEntry);

        when(consentRepository.getConsentDetails(consentRequestId)).thenReturn(Flux.fromIterable(consentDetails));
        when(dataFlowRepository.getTransactionId(consentId)).thenReturn(Mono.just(transactionId));
        when(healthInformationRepository.getHealthInformation(transactionId)).thenReturn(Flux.just(entry));

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

        when(dataFlowRepository.insertHealthInformation(transactionId, entry)).thenReturn(Mono.empty());

        var errorResponse = new ErrorRepresentation(new Error(
                ErrorCode.INVALID_DATA_FLOW_ENTRY,
                "Entry must either have content or provide a link."));
        var errorResponseJson = new ObjectMapper().writeValueAsString(errorResponse);

        webTestClient
                .post()
                .uri("/data/notification")
                .header("Authorization", "R2FuZXNoQG5jZw==")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .json(errorResponseJson);
    }
}
