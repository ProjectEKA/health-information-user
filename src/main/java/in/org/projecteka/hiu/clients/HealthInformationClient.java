package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.failedToNotifyCM;
import static in.org.projecteka.hiu.common.Constants.X_CM_ID;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@AllArgsConstructor
public class HealthInformationClient {
    private final WebClient.Builder builder;
    private final GatewayProperties gatewayProperties;

    public Mono<HealthInformation> informationFrom(String url) {
        return builder.build()
                .get()
                .uri(url)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == NOT_FOUND,
                        clientResponse -> Mono.error(new Throwable("Health information not found")))
                .onStatus(httpStatus -> httpStatus == UNAUTHORIZED,
                        clientResponse -> Mono.error(new Throwable("Unauthorized")))
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse ->
                        Mono.error(new Throwable("Unknown error occurred")))
                .bodyToMono(HealthInformation.class);
    }

    public Mono<Void> notifyHealthInfo(HealthInfoNotificationRequest notificationRequest,
                                       String token,
                                       String consentManagerId) {
        return builder.build()
                .post()
                .uri(gatewayProperties.getBaseUrl() + "/health-information/notify")
                .header(AUTHORIZATION, token)
                .header(X_CM_ID, consentManagerId)
                .body(Mono.just(notificationRequest), HealthInfoNotificationRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse -> Mono.error(failedToNotifyCM()))
                .toBodilessEntity().then();
    }
}
