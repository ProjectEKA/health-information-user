package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.HEALTH_INFO_QUEUE;

@AllArgsConstructor
public class HealthInformationPublisher {
    private static final Logger logger = Logger.getLogger(HealthInformationPublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;

    @SneakyThrows
    public Mono<Void> publish(ConsentArtefactReference consentArtefactReference) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(HEALTH_INFO_QUEUE);

        if (destinationInfo == null) {
            logger.info(HEALTH_INFO_QUEUE + " not found");
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
