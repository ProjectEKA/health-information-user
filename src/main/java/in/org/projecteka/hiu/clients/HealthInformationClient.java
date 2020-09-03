package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static in.org.projecteka.hiu.ClientError.failedToNotifyCM;
import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;
import static in.org.projecteka.hiu.common.Constants.X_CM_ID;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static reactor.core.publisher.Mono.error;

public class HealthInformationClient {
    private final WebClient client;
    private final GatewayProperties gatewayProperties;
    private static final Logger logger = getLogger(HealthInformationClient.class);

    public HealthInformationClient(WebClient.Builder client, GatewayProperties gatewayProperties) {
        this.client = client.build();
        this.gatewayProperties = gatewayProperties;
    }

    public Mono<HealthInformation> informationFrom(String url) {
        return client
                .get()
                .uri(url)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                .retrieve()
                .onStatus(httpStatus -> httpStatus == NOT_FOUND,
                        clientResponse -> clientResponse.bodyToMono(Properties.class)
                                .doOnNext(properties -> logger.error(properties.toString()))
                                .then(error(new Throwable("Health information not found"))))
                .onStatus(httpStatus -> httpStatus == UNAUTHORIZED,
                        clientResponse -> clientResponse.bodyToMono(Properties.class)
                                .doOnNext(properties -> logger.error(properties.toString()))
                                .then(error(new Throwable("Unauthorized"))))
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> clientResponse.bodyToMono(Properties.class)
                                .doOnNext(properties -> logger.error(properties.toString()))
                                .then(error(new Throwable("Unknown error occurred"))))
                .bodyToMono(HealthInformation.class);
    }

    public Mono<Void> notifyHealthInfo(HealthInfoNotificationRequest notificationRequest,
                                       String token,
                                       String consentManagerId) {
        return client
                .post()
                .uri(gatewayProperties.getBaseUrl() + "/health-information/notify")
                .header(AUTHORIZATION, token)
                .header(X_CM_ID, consentManagerId)
                .header(CORRELATION_ID, MDC.get(CORRELATION_ID))
                .body(Mono.just(notificationRequest), HealthInfoNotificationRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> clientResponse.bodyToMono(Properties.class)
                                .doOnNext(properties -> logger.error(properties.toString()))
                                .then(error(failedToNotifyCM())))
                .toBodilessEntity().then();
    }
}
