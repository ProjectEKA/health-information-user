package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataNotificationRequest;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class DataFlowUserJourneyTest {
    private static MockWebServer dataFlowServer = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DataFlowRepository dataFlowRepository;

    @MockBean
    private DestinationsConfig destinationsConfig;

    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

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
        DataNotificationRequest dataNotificationRequest = dataNotificationRequest().build();

        dataFlowServer.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json"));

        webTestClient
                .post()
                .uri("/data/notification")
                .header("Authorization", "AuthToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataNotificationRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk();
    }
}
