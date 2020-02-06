package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.consent.model.dataflow.Consent;
import in.org.projecteka.hiu.consent.model.dataflow.DataFlowRequest;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_REQUEST_QUEUE;

@AllArgsConstructor
public class DataFlowRequestPublisher {
    private AmqpTemplate amqpTemplate;
    private DestinationsConfig destinationsConfig;

    @SneakyThrows
    public Mono<Void> broadcastDataFlowRequest(String consentArtefactId, String signature, String callBackUrl) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(DATA_FLOW_REQUEST_QUEUE);

        if (destinationInfo == null) {
            return Mono.error(new Exception("Queue doesn't exists"));
        }

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(
                    destinationInfo.getExchange(),
                    destinationInfo.getRoutingKey(),
                    DataFlowRequest.builder()
                            .consent(Consent.builder().
                                    id(consentArtefactId)
                                    .digitalSignature(signature)
                                    .build())
                            .callBackUrl(callBackUrl)
                            .build());
            monoSink.success();
        });
    }
}
