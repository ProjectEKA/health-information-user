package in.org.projecteka.hiu.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ExternalIdentityProviderTest {
    private @Captor
    ArgumentCaptor<ClientRequest> captor;
    @Mock
    private ExchangeFunction exchangeFunction;

    private ExternalIdentityProvider externalIdentityProvider;
    private String idpCertPath;
    private String idpAuthURL;

    @BeforeEach
    void setUp() {
        idpCertPath = "http://localhost:8000/idpcert/";
        idpAuthURL = "http://localhost:8000/idpauth/";
        IDPProperties idpProperties = IDPProperties
                .builder()
                .externalIdpAuthURL(idpAuthURL)
                .externalIdpClientId("xyz")
                .externalIdpClientSecret("secret")
                .externalIdpCertPath(idpCertPath)
                .build();

        MockitoAnnotations.initMocks(this);
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        externalIdentityProvider = new ExternalIdentityProvider(webClientBuilder, idpProperties);
    }

    @Test
    void shouldFetchHasCertUsingGatewayToken() {
        ClientResponse tokenResponse = ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"access_token\": \"xyz token\"}")
                .build();

        ClientResponse certsResponse = ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("-----BEGIN PUBLIC KEY-----\nXYZ CERT\n-----END PUBLIC KEY-----")
                .build();

        when(exchangeFunction.exchange(captor.capture())).thenReturn(Mono.just(tokenResponse), Mono.just(certsResponse));

        Mono<String> certificate = externalIdentityProvider.fetchCertificate();

        StepVerifier.create(certificate).assertNext(
                cert -> assertThat(cert).isEqualTo("XYZ CERT")
        ).verifyComplete();

        List<ClientRequest> allCaptures = captor.getAllValues();
        assertThat(allCaptures.get(0).url().toString()).isEqualTo(idpAuthURL);
        assertThat(allCaptures.get(1).url().toString()).isEqualTo(idpCertPath);
        assertThat(allCaptures.get(1).headers()).containsEntry("Authorization", asList("Bearer xyz token"));
    }
}