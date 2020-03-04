package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.clients.PatientSearchException.notFound;
import static in.org.projecteka.hiu.clients.PatientSearchException.unknown;
import static java.lang.String.format;
import static java.util.function.Predicate.not;

public class PatientServiceClient {

    private final WebClient builder;

    public PatientServiceClient(WebClient.Builder builder,
                                ConsentManagerServiceProperties properties) {
        this.builder = builder.baseUrl(properties.getUrl()).build();
    }

    public Mono<Patient> patientWith(String id, String token) {
        return builder.
                get()
                .uri(format("/users/%s", id))
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND,
                        clientResponse -> Mono.error(notFound()))
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(unknown()))
                .bodyToMono(Patient.class);
    }
}
