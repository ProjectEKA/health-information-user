package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.common.TraceableMessage;
import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.DataFlowDelete;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.common.Serializer.to;

@AllArgsConstructor
public class DataFlowDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(DataFlowDeleteListener.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final DataFlowRepository dataFlowRepository;
    private final HealthInformationRepository healthInformationRepository;
    private final DataFlowServiceProperties dataFlowServiceProperties;
    private final LocalDataStore localDataStore;
    private final RabbitQueueNames queueNames;

    @PostConstruct
    @SneakyThrows
    public void subscribe() {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(queueNames.getDataFlowDeleteQueue());
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            var traceableMessage = to(message.getBody(), TraceableMessage.class);
            var mayBeDataFlow = to((byte[]) traceableMessage.get().getMessage(), DataFlowDelete.class);
            String correlationId = to(traceableMessage.get().getCorrelationId(), String.class);
            mayBeDataFlow.ifPresentOrElse(dataFlowDelete -> {
                MDC.put(Constants.CORRELATION_ID, correlationId);
                logger.info("Received data flow delete for consent artefact id: {}", dataFlowDelete.getConsentId());
                String transactionId = dataFlowRepository.getTransactionId(dataFlowDelete.getConsentId()).block();
                if (transactionId != null) {
                    healthInformationRepository.deleteHealthInformation(transactionId);
                    Path pathToTransactionDirectory = Paths.get(dataFlowServiceProperties.getLocalStoragePath(),
                            getLocalDirectoryName(dataFlowDelete.getConsentRequestId()),
                            getLocalDirectoryName(transactionId));
                    localDataStore.deleteExpiredConsentData(pathToTransactionDirectory);
                }
            }, () -> logger.info("Failed to delete data flow message"));
            MDC.clear();
        };

        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    @SneakyThrows
    private String getLocalDirectoryName(String directoryName) {
        return String.format("%s", TokenUtils.encode(directoryName));
    }
}
