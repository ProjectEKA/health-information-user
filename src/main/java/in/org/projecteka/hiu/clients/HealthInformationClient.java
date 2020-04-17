package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.failedToNotifyCM;
import static java.util.function.Predicate.not;

@AllArgsConstructor
public class HealthInformationClient {
    private final WebClient.Builder builder;
    private final ConsentManagerServiceProperties consentManagerServiceProperties;

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

    public Mono<Void> notifyHealthInfo(HealthInfoNotificationRequest notificationRequest, String token) {
        return builder.build()
                .post()
                .uri(consentManagerServiceProperties.getUrl() + "/health-information/notification")
                .header("Authorization", token)
                .body(Mono.just(notificationRequest), HealthInfoNotificationRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(failedToNotifyCM()))
                .toBodilessEntity().then();
    }

}
