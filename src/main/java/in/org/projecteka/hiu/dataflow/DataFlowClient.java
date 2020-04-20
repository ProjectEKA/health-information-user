package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.consent.ConsentException.failedToInitiateDataFlowRequest;
import static java.util.function.Predicate.not;

@AllArgsConstructor
public class DataFlowClient {
    private final WebClient.Builder webClientBuilder;
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
}
