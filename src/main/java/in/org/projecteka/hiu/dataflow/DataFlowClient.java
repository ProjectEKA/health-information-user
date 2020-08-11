package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static in.org.projecteka.hiu.consent.ConsentException.failedToInitiateDataFlowRequest;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Mono.error;

@AllArgsConstructor
public class DataFlowClient {
    private final WebClient.Builder webClientBuilder;
    private final GatewayProperties gatewayProperties;
    private static final Logger logger = getLogger(DataFlowClient.class);

    public Mono<Void> initiateDataFlowRequest(GatewayDataFlowRequest dataFlowRequest, String token, String cmSuffix) {
        return webClientBuilder.build()
                .post()
                .uri(gatewayProperties.getBaseUrl() + "/health-information/cm/request")
                .header("Authorization", token)
                .header("X-CM-ID", cmSuffix)
                .body(Mono.just(dataFlowRequest), GatewayDataFlowRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> clientResponse.bodyToMono(Properties.class)
                                .doOnNext(properties -> logger.error(properties.toString()))
                                .then(error(failedToInitiateDataFlowRequest())))
                .toBodilessEntity()
                .then();
    }
}
