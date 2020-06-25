package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.GatewayProperties;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResponse;
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
    private final ConsentManagerServiceProperties consentManagerServiceProperties;

    public Mono<DataFlowRequestResponse> initiateDataFlowRequest(DataFlowRequest dataFlowRequest, String token) {
        return webClientBuilder.build()
                .post()
                .uri(consentManagerServiceProperties.getUrl() + "/health-information/request")
                .header("Authorization", token)
                .body(Mono.just(dataFlowRequest), DataFlowRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful),
                        clientResponse -> Mono.error(failedToInitiateDataFlowRequest()))
                .bodyToMono(DataFlowRequestResponse.class);
    }


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
