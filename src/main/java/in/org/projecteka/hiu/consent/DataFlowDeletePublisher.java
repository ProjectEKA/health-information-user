package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.dataflow.model.DataFlowDelete;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.queueNotFound;

@AllArgsConstructor
public class DataFlowDeletePublisher {
    private static final Logger logger = LoggerFactory.getLogger(DataFlowDeletePublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;
    private final RabbitQueueNames queueNames;

    @SneakyThrows
    public Mono<Void> broadcastConsentExpiry(String consentArtefactId, String consentRequestId) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(queueNames.getDataFlowDeleteQueue());

        if (destinationInfo == null) {
            logger.info(queueNames.getDataFlowDeleteQueue() + " not found");
            throw queueNotFound();
        }

        return Mono.create(monoSink -> {
            try {
                sendMessage(DataFlowDelete.builder().consentId(consentArtefactId).consentRequestId(consentRequestId).build(),
                        destinationInfo.getExchange(),
                        destinationInfo.getRoutingKey());
                logger.info(String.format("Broadcasting consent expiry with consent id : %s", consentArtefactId));
                monoSink.success();
            } catch (AmqpException e) {
                logger.error(e.getMessage(), e);
                monoSink.error(new Exception("Failed to push message to the data flow delete queue"));
            }
        });
    }

    private void sendMessage(Object message, String exchange, String routingKey) {
        amqpTemplate.convertAndSend(exchange, routingKey, message);
    }
}
