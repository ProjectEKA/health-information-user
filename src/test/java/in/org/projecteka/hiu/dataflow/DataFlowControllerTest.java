package in.org.projecteka.hiu.dataflow;

import com.nimbusds.jose.jwk.JWKSet;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.ErrorCode;
import in.org.projecteka.hiu.ErrorRepresentation;
import in.org.projecteka.hiu.common.Authenticator;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import static in.org.projecteka.hiu.common.Constants.PATH_DATA_TRANSFER;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DataFlowControllerTest {
    @Autowired
    private WebTestClient webClient;

    @MockBean
    private DataFlowService dataFlowService;

    @SuppressWarnings("unused")
    @MockBean
    private DataAvailabilityListener dataAvailabilityListener;

    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowRequestListener dataFlowRequestListener;

    @SuppressWarnings("unused")
    @MockBean
    private DataFlowDeleteListener dataFlowDeleteListener;

    @MockBean
    @Qualifier("centralRegistryJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean
    @Qualifier("identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    @Qualifier("userAuthenticator")
    private Authenticator authenticator;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldAcceptTheDataTransferRequest() {
        String jsonBody = "{\"pageNumber\":1,\"pageCount\":1,\"transactionId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"," +
                "\"entries\":[{\"content\":\"Encrypted content of data packaged in FHIR bundle\",\"media\":\"application/fhir+json\"," +
                "\"checksum\":\"string\",\"careContextReference\":\"RVH1008\"},{\"link\":\"https://data-from.net/sa2321afaf12e13\"," +
                "\"media\":\"application/fhir+json\",\"checksum\":\"string\",\"careContextReference\":\"NCC1701\"}],\"keyMaterial\"" +
                ":{\"cryptoAlg\":\"ECDH\",\"curve\":\"Curve25519\",\"dhPublicKey\":{\"expiry\":\"2021-02-23T06:01:08.552Z\"," +
                "\"parameters\":\"Curve25519/32byte random key\",\"keyValue\":\"string\"}," +
                "\"nonce\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}}";

        webClient.post()
                .uri(PATH_DATA_TRANSFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .exchange()
                .expectStatus()
                .isAccepted();

    }

    @Test
    public void shouldGiveErrorWhenPageCountIsMoreThanOne() {
        String jsonBody = "{\"pageNumber\":1,\"pageCount\":10,\"transactionId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"," +
                "\"entries\":[{\"content\":\"Encrypted content of data packaged in FHIR bundle\",\"media\":\"application/fhir+json\"," +
                "\"checksum\":\"string\",\"careContextReference\":\"RVH1008\"},{\"link\":\"https://data-from.net/sa2321afaf12e13\"," +
                "\"media\":\"application/fhir+json\",\"checksum\":\"string\",\"careContextReference\":\"NCC1701\"}],\"keyMaterial\"" +
                ":{\"cryptoAlg\":\"ECDH\",\"curve\":\"Curve25519\",\"dhPublicKey\":{\"expiry\":\"2021-02-23T06:01:08.552Z\"," +
                "\"parameters\":\"Curve25519/32byte random key\",\"keyValue\":\"string\"}," +
                "\"nonce\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}}";

        webClient.post()
                .uri(PATH_DATA_TRANSFER)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ErrorRepresentation.class)
                .value(response -> {
                    assertEquals(ErrorCode.INVALID_REQUEST, response.getError().getCode());
                    assertEquals("Multi page data transfer is not supported yet.", response.getError().getMessage());
                });

    }
}