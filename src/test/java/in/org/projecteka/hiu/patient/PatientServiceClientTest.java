package in.org.projecteka.hiu.patient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.clients.PatientRepresentation;
import in.org.projecteka.hiu.clients.PatientServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Supplier;

import static in.org.projecteka.hiu.consent.TestBuilders.patientRepresentation;
import static in.org.projecteka.hiu.patient.TestBuilders.string;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PatientServiceClientTest {

    static final String BASE_URL = "http://url";
    @Captor
    ArgumentCaptor<ClientRequest> captor;
    PatientServiceClient patientServiceClient;
    @Mock
    private ExchangeFunction exchangeFunction;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(exchangeFunction);
        patientServiceClient = new PatientServiceClient(
                webClientBuilder,
                new ConsentManagerServiceProperties(BASE_URL, "@ncg"));
    }

    @Test
    void returnPatientWhenUserExists() throws JsonProcessingException {
        String patientId = "consentArtefactPatient-id@ncg";
        var patientRep = patientRepresentation().build();
        var response = new ObjectMapper().writeValueAsString(patientRep);
        when(exchangeFunction.exchange(captor.capture()))
                .thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(response).build()));

        Supplier<Mono<PatientRepresentation>> action = () -> patientServiceClient.patientWith(patientId, string());

        StepVerifier.create(action.get()).expectNext(patientRep).verifyComplete();
        assertThat(captor.getValue().url().toString())
                .isEqualTo(format("%s/users/consentArtefactPatient-id@ncg", BASE_URL));
    }
}