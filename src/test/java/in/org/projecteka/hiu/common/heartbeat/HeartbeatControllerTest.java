package in.org.projecteka.hiu.common.heartbeat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.TestBuilders;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import in.org.projecteka.hiu.common.heartbeat.model.Status;
import in.org.projecteka.hiu.consent.ConceptValidator;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowDeleteListener;
import in.org.projecteka.hiu.dataflow.DataFlowRequestListener;
import in.org.projecteka.hiu.dataprocessor.DataAvailabilityListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "6000")
class HeartbeatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    private Heartbeat heartbeat;

    @MockBean
    DestinationsConfig destinationsConfig;

    @MockBean
    DataFlowRequestPublisher dataFlowRequestPublisher;

    @MockBean
    DataFlowRequestListener dataFlowRequestListener;

    @MockBean
    DataFlowDeleteListener dataFlowDeleteListener;

    @MockBean
    DataAvailabilityListener dataAvailabilityListener;

    @MockBean
    ConceptValidator conceptValidator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void shouldGiveHIUStatusAsUp() throws JsonProcessingException {
        var heartbeatResponse = HeartbeatResponse.builder()
                .timeStamp(LocalDateTime.now(ZoneOffset.UTC))
                .status(Status.UP)
                .build();
        var heartbeatResponseJson = TestBuilders.OBJECT_MAPPER.writeValueAsString(heartbeatResponse);

        when(heartbeat.getStatus()).thenReturn(Mono.just(heartbeatResponse));

        webTestClient.get()
                .uri(Constants.PATH_READINESS)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json(heartbeatResponseJson);
    }
    @Test
    void shouldGiveHIUStatusAsUpForLiveliness() throws JsonProcessingException {
        webTestClient.get()
                .uri(Constants.PATH_HEARTBEAT)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void shouldGiveHIUStatusAsDown() throws JsonProcessingException {
        var heartbeatResponse = HeartbeatResponse.builder()
                .timeStamp(LocalDateTime.now(ZoneOffset.UTC))
                .status(Status.DOWN)
                .error(new Error(ErrorCode.SERVICE_DOWN,"Service Down"))
                .build();
        var heartbeatResponseJson = TestBuilders.OBJECT_MAPPER.writeValueAsString(heartbeatResponse);

        when(heartbeat.getStatus()).thenReturn(Mono.just(heartbeatResponse));

        webTestClient.get()
                .uri(Constants.PATH_READINESS)
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody()
                .json(heartbeatResponseJson);
    }
}
