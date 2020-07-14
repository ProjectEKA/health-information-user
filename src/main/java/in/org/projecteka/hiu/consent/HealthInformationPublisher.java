package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.queueNotFound;

@AllArgsConstructor
public class HealthInformationPublisher {
    private static final Logger logger = Logger.getLogger(HealthInformationPublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;
    private final RabbitQueueNames queueNames;

    @SneakyThrows
    public Mono<Void> publish(ConsentArtefactReference consentArtefactReference) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(queueNames.getHealthInfoQueue());

        if (destinationInfo == null) {
            logger.info(queueNames.getHealthInfoQueue() + " not found");
            throw queueNotFound();
        }

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(
                    destinationInfo.getExchange(),
                    destinationInfo.getRoutingKey(),
                    consentArtefactReference);
            logger.info("Broadcasting health data removal request with consent id : " + consentArtefactReference.getId());
            monoSink.success();
        });
    }
}
