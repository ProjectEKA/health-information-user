package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.consent.model.dataflow.Consent;
import in.org.projecteka.hiu.consent.model.dataflow.DataFlowRequest;
import in.org.projecteka.hiu.consent.model.dataflow.DateRange;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.queueNotFound;

@AllArgsConstructor
public class DataFlowRequestPublisher {
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;
    private final RabbitQueueNames queueNames;

    @SneakyThrows
    public Mono<Void> broadcastDataFlowRequest(String consentArtefactId, in.org.projecteka.hiu.consent.model.DateRange dateRange, String signature, String dataPushUrl) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(queueNames.getDataFlowRequestQueue());

        if (destinationInfo == null) {
            logger.info(queueNames.getDataFlowRequestQueue() + " not found");
            throw queueNotFound();
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
                            .dateRange(DateRange.builder()
                                    .from(dateRange.getFrom())
                                    .to(dateRange.getTo())
                                    .build())
                            .dataPushUrl(dataPushUrl)
                            .build());
            logger.info("Broadcasting data flow request with consent id : " + consentArtefactId);
            monoSink.success();
        });
    }
}
