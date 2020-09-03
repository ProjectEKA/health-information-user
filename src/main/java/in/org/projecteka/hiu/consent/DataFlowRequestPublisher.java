package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.common.TraceableMessage;
import in.org.projecteka.hiu.consent.model.dataflow.Consent;
import in.org.projecteka.hiu.consent.model.dataflow.DataFlowRequest;
import in.org.projecteka.hiu.consent.model.dataflow.DateRange;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;

@AllArgsConstructor
public class DataFlowRequestPublisher {
    private static final Logger logger = LoggerFactory.getLogger(DataFlowRequestPublisher.class);
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;
    private final RabbitQueueNames queueNames;

    @SneakyThrows
    public Mono<Void> broadcastDataFlowRequest(String consentArtefactId, in.org.projecteka.hiu.consent.model.DateRange dateRange, String signature, String dataPushUrl) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(queueNames.getDataFlowRequestQueue());
         DataFlowRequest request = DataFlowRequest.builder()
                .consent(Consent.builder()
                        .id(consentArtefactId)
                        .digitalSignature(signature)
                        .build())
                .dateRange(DateRange.builder()
                        .from(dateRange.getFrom())
                        .to(dateRange.getTo())
                        .build())
                .dataPushUrl(dataPushUrl)
                .build();
       TraceableMessage traceableMessage = TraceableMessage.builder()
                .correlationId(MDC.get(CORRELATION_ID))
                .message(request)
                .build();

        if (destinationInfo == null) {
            logger.info(queueNames.getDataFlowRequestQueue() + " not found");
            throw queueNotFound();
        }

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(
                    destinationInfo.getExchange(),
                    destinationInfo.getRoutingKey(),
                    traceableMessage);
            logger.info("Broadcasting data flow request with consent id : " + consentArtefactId);
            monoSink.success();
        });
    }
}
