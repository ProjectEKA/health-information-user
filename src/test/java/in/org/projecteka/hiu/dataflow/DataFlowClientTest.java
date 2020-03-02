package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResponse;
import in.org.projecteka.hiu.dataflow.model.HIDataRange;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.text.ParseException;
import java.util.Objects;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequest;
import static in.org.projecteka.hiu.dataflow.TestBuilders.string;
import static in.org.projecteka.hiu.dataflow.Utils.toDate;
import static org.assertj.core.api.Assertions.assertThat;

public class DataFlowClientTest {
    private DataFlowClient dataFlowClient;
    private MockWebServer mockWebServer;

    @BeforeEach
    public void init() {
        mockWebServer = new MockWebServer();
        WebClient.Builder webClientBuilder = WebClient.builder();
        ConsentManagerServiceProperties consentManagerServiceProperties =
                new ConsentManagerServiceProperties(mockWebServer.url("").toString());
        HiuProperties hiuProperties = new HiuProperties("10000005", "Max Health Care", "localhost:8080", string());
        dataFlowClient = new DataFlowClient(webClientBuilder, hiuProperties, consentManagerServiceProperties);
    }

    @Test
    public void shouldCreateConsentRequest() throws JsonProcessingException, InterruptedException, ParseException {
        String transactionId = "transactionId";
        DataFlowRequestResponse dataFlowRequestResponse =
                DataFlowRequestResponse.builder().transactionId(transactionId).build();
        var dataFlowRequestResponseJson = new ObjectMapper().writeValueAsString(dataFlowRequestResponse);
        DataFlowRequest dataFlowRequest = dataFlowRequest().build();
        dataFlowRequest.setHiDataRange(HIDataRange.builder().from(toDate("2020-01-14T08:47:48Z")).to(toDate("2020" +
                "-01-20T08:47:48Z")).build());
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(dataFlowRequestResponseJson));

        StepVerifier.create(dataFlowClient.initiateDataFlowRequest(dataFlowRequest))
                .assertNext(
                        response -> {
                            assertThat(response.getTransactionId()).isEqualTo(transactionId);
                        })
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(Objects.requireNonNull(recordedRequest.getRequestUrl()).toString())
                .isEqualTo(mockWebServer.url("") + "health-information/request");
        assertThat(recordedRequest.getBody().readUtf8())
                .isEqualTo(new ObjectMapper().writeValueAsString(dataFlowRequest));
    }
}
