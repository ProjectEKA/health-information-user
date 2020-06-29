package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataflow.model.DateRange;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.Objects;
import java.util.UUID;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequest;
import static in.org.projecteka.hiu.dataflow.TestBuilders.string;
import static in.org.projecteka.hiu.dataflow.Utils.toDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class DataFlowClientTest {
    private DataFlowClient dataFlowClient;
    private MockWebServer mockWebServer;

    @Mock
    GatewayProperties gatewayProperties;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
        mockWebServer = new MockWebServer();
        ExchangeStrategies strategies = ExchangeStrategies
                .builder()
                .codecs(clientDefaultCodecsConfigurer -> {
                    ObjectMapper mapper = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    clientDefaultCodecsConfigurer.defaultCodecs()
                            .jackson2JsonEncoder(new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
                    clientDefaultCodecsConfigurer.defaultCodecs()
                            .jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));

                }).build();
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeStrategies(strategies);
        dataFlowClient = new DataFlowClient(webClientBuilder, gatewayProperties);
    }

    @Test
    void shouldCreateConsentRequestUsingGateway() throws InterruptedException {
        var dataFlowRequest = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-14T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();
        var gatewayDataFlowRequest = new GatewayDataFlowRequest(UUID.randomUUID(),string(),dataFlowRequest);
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(202));
        when(gatewayProperties.getBaseUrl()).thenReturn(mockWebServer.url("").toString());

        StepVerifier.create(dataFlowClient.initiateDataFlowRequest(gatewayDataFlowRequest, string(), string()))
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(Objects.requireNonNull(recordedRequest.getRequestUrl()).toString())
                .isEqualTo(mockWebServer.url("") + "health-information/cm/request");
    }
}
