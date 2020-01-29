package in.org.projecteka.hiu.patient;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.consent.TokenUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.patient.PatientRepresentation.from;
import static in.org.projecteka.hiu.patient.PatientSearchException.notFound;
import static in.org.projecteka.hiu.patient.PatientSearchException.unknown;
import static java.lang.String.format;
import static java.util.function.Predicate.not;

public class PatientServiceClient {

    private final WebClient builder;
    private HiuProperties hiuProperties;

    public PatientServiceClient(WebClient.Builder builder,
                                ConsentManagerServiceProperties properties,
                                HiuProperties hiuProperties) {
        this.builder = builder.baseUrl(properties.getUrl()).build();
        this.hiuProperties = hiuProperties;
    }

    public Mono<SearchRepresentation> patientWith(String id) {
        return builder.
                get()
                .uri(format("/users/%s", id))
                .header(HttpHeaders.AUTHORIZATION, TokenUtils.encodeHIUId(hiuProperties.getId()))
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND,
                        clientResponse -> Mono.error(notFound()))
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(unknown()))
                .bodyToMono(Patient.class)
                .map(patient -> new SearchRepresentation(from(patient)));
    }
}
