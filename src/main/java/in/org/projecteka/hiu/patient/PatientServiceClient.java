package in.org.projecteka.hiu.patient;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static java.lang.String.format;

public class PatientServiceClient {

    private final WebClient builder;

    public PatientServiceClient(WebClient.Builder builder, ConsentManagerServiceProperties properties) {
        this.builder = builder.baseUrl(properties.getUrl()).build();
    }

    public Mono<SearchRepresentation> patientWith(String id) {
        return builder.
                get()
                .uri(format("/patients/%s", id))
                .retrieve()
                .bodyToMono(SearchRepresentation.class);
    }
}
