package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.Objects;

import static in.org.projecteka.hiu.consent.TestBuilders.randomString;
import static org.assertj.core.api.Assertions.assertThat;


import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRepresentation;

public class ConsentManagerClientTest {
    private ConsentManagerClient consentManagerClient;
    private MockWebServer mockWebServer;

    @BeforeEach
    public void init() {
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
        ConsentManagerServiceProperties consentManagerServiceProperties =
                new ConsentManagerServiceProperties(mockWebServer.url("").toString(), "@ncg");
        consentManagerClient = new ConsentManagerClient(webClientBuilder, consentManagerServiceProperties);
    }

    @Test
    void shouldCreateConsentRequest() throws JsonProcessingException, InterruptedException {
        String consentRequestId = "consent-request-id";
        var consentCreationResponse = consentCreationResponse().id(consentRequestId).build();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var consentCreationResponseJson = objectMapper.writeValueAsString(consentCreationResponse);
        var consentRepresentation = consentRepresentation().build();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(consentCreationResponseJson));

        StepVerifier.create(consentManagerClient.createConsentRequest(consentRepresentation, randomString()))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(consentCreationResponse.getId()))
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(Objects.requireNonNull(recordedRequest.getRequestUrl()).toString())
                .isEqualTo(mockWebServer.url("") + "consent-requests");
        assertThat(recordedRequest.getBody().readUtf8())
                .isEqualTo(objectMapper.writeValueAsString(consentRepresentation));
    }
}
