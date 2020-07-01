package in.org.projecteka.hiu.common.heartbeat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.Error;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import in.org.projecteka.hiu.common.heartbeat.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "6000")
@ActiveProfiles("dev")
class HeartbeatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @SuppressWarnings("unused")
    @MockBean(name = "centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean(name = "identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    private Heartbeat heartbeat;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGiveCMStatusAsUp() throws JsonProcessingException {
        var heartbeatResponse = HeartbeatResponse.builder()
                .timeStamp(Instant.now().toString())
                .status(Status.UP)
                .build();
        var heartbeatResponseJson = TestBuilders.OBJECT_MAPPER.writeValueAsString(heartbeatResponse);

        when(heartbeat.getStatus()).thenReturn(Mono.just(heartbeatResponse));

        webTestClient.get()
                .uri("/v1/heartbeat")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json(heartbeatResponseJson);
    }

    @Test
    public void shouldGiveCMStatusAsDown() throws JsonProcessingException {
        var heartbeatResponse = HeartbeatResponse.builder()
                .timeStamp(Instant.now().toString())
                .status(Status.DOWN)
                .error(new Error(ErrorCode.SERVICE_DOWN,"Service Down"))
                .build();
        var heartbeatResponseJson = TestBuilders.OBJECT_MAPPER.writeValueAsString(heartbeatResponse);

        when(heartbeat.getStatus()).thenReturn(Mono.just(heartbeatResponse));

        webTestClient.get()
                .uri("/v1/heartbeat")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json(heartbeatResponseJson);
    }
}