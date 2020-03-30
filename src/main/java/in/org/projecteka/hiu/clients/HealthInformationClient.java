package in.org.projecteka.hiu.clients;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static java.util.function.Predicate.not;

@AllArgsConstructor
public class HealthInformationClient {
    private final WebClient.Builder builder;

    public Mono<HealthInformation> getHealthInformationFor(String url) {
        return builder.build()
                .get()
                .uri(url)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND,
                        clientResponse -> Mono.error(new Throwable("Health information not found")))
                .onStatus(httpStatus -> httpStatus == HttpStatus.UNAUTHORIZED,
                        clientResponse -> Mono.error(new Throwable("Unauthorized")))
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse ->
                        Mono.error(new Throwable("Unknown error occured")))
                .bodyToMono(HealthInformation.class);
    }

}
