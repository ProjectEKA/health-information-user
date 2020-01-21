package in.org.projecteka.hiu.patient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
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

import static in.org.projecteka.hiu.patient.PatientRepresentation.from;
import static in.org.projecteka.hiu.patient.TestBuilders.patient;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PatientServiceClientTest {

    @Captor
    ArgumentCaptor<ClientRequest> captor;

    PatientServiceClient patientServiceClient;

    @Mock
    private ExchangeFunction exchangeFunction;

    static final String BASE_URL = "http://url";

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(exchangeFunction);
        patientServiceClient = new PatientServiceClient(webClientBuilder, new ConsentManagerServiceProperties(BASE_URL));
    }

    @Test
    void returnPatientWhenUserExists() throws JsonProcessingException {
        String patientId = "patient-id@ncg";
        var patient = patient().build();
        var searchRepresentation = new SearchRepresentation(from(patient));
        var response = new ObjectMapper().writeValueAsString(patient);
        when(exchangeFunction.exchange(captor.capture()))
                .thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(response).build()));

        Supplier<Mono<SearchRepresentation>> action = () ->  patientServiceClient.patientWith(patientId);

        StepVerifier.create(action.get()).expectNext(searchRepresentation).verifyComplete();
        assertThat(captor.getValue().url().toString()).isEqualTo(format("%s/users/patient-id@ncg", BASE_URL));
    }
}