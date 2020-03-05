package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_PROCESS_QUEUE;

@AllArgsConstructor
public class DataAvailabilityPublisher {

    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);
    private AmqpTemplate amqpTemplate;
    private DestinationsConfig destinationsConfig;

    @SneakyThrows
    public Mono<Void> broadcastDataAvailability(Map<String, String> contentRef) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(DATA_FLOW_PROCESS_QUEUE);

        if (destinationInfo == null) {
            logger.info(String.format("Queue %s not found",DATA_FLOW_PROCESS_QUEUE));
            throw ClientError.queueNotFound();
        }

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(
                    destinationInfo.getExchange(),
                    destinationInfo.getRoutingKey(),
                    contentRef);
            logger.info("Broadcasting data availability for transaction id : " + contentRef.toString());
            monoSink.success();
        });

    }
}
