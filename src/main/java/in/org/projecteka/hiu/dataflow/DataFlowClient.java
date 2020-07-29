package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.consent.ConsentException.failedToInitiateDataFlowRequest;
import static java.util.function.Predicate.not;

@AllArgsConstructor
public class DataFlowClient {
    private final WebClient.Builder webClientBuilder;
    private final GatewayProperties gatewayProperties;

    public Mono<Void> initiateDataFlowRequest(GatewayDataFlowRequest dataFlowRequest, String token, String cmSuffix) {
        return webClientBuilder.build()
                .post()
                .uri( gatewayProperties.getBaseUrl() + "/health-information/cm/request")
                .header("Authorization", token)
                .header("X-CM-ID", cmSuffix)
                .body(Mono.just(dataFlowRequest), GatewayDataFlowRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(failedToInitiateDataFlowRequest()))
                .toBodilessEntity()
                .then();
    }
}
