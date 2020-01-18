package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.model.consentmanager.ConsentRepresentation;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;


import static in.org.projecteka.hiu.consent.TestBuilders.consentCreationResponse;
import static in.org.projecteka.hiu.consent.TestBuilders.consentRepresentation;

public class ConsentManagerClientTest {
    private ConsentManagerClient consentManagerClient;
    private MockWebServer mockWebServer;
    private String baseUrl;

    @BeforeEach
    public void init() {
        mockWebServer = new MockWebServer();
        baseUrl = mockWebServer.url("/").toString();
        WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(baseUrl);
        ConsentManagerServiceProperties consentManagerServiceProperties =
                new ConsentManagerServiceProperties("");
        HiuProperties hiuProperties = new HiuProperties("10000005", "Max Health Care");
        consentManagerClient = new ConsentManagerClient(webClientBuilder, consentManagerServiceProperties, hiuProperties);
    }


    @Test
    public void shouldCreateConsentRequest() throws JsonProcessingException, InterruptedException {
        String consentRequestId = "consent-request-id";
        var consentCreationResponse = consentCreationResponse().id(consentRequestId).build();
        var consentCreationResponseJson = new ObjectMapper().writeValueAsString(consentCreationResponse);
        mockWebServer.enqueue( new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(consentCreationResponseJson));

        ConsentRepresentation consentRepresentation = consentRepresentation().build();

        StepVerifier.create(consentManagerClient.createConsentRequestInConsentManager(consentRepresentation))
                .assertNext(
                        response -> {
                            assertThat(response.getId()).isEqualTo(consentCreationResponse.getId());
                })
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertThat(recordedRequest.getRequestUrl().toString()).isEqualTo(baseUrl + "consent-requests");
        assertThat(recordedRequest.getBody().readUtf8())
                .isEqualTo(new ObjectMapper().writeValueAsString(consentRepresentation));
    }
}
