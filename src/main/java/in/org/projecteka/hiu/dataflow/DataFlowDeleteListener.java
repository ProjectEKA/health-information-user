package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.DataFlowDelete;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static in.org.projecteka.hiu.ClientError.queueNotFound;

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
            DataFlowDelete dataFlowDelete = convertToDataFlowDelete(message.getBody());
            logger.info(String.format("Received data flow delete for consent artefact id: %s", dataFlowDelete.getConsentId()));
            String transactionId = dataFlowRepository.getTransactionId(dataFlowDelete.getConsentId()).block();
            if (transactionId != null) {
                healthInformationRepository.deleteHealthInformation(transactionId);
                Path pathToTransactionDirectory = Paths.get(dataFlowServiceProperties.getLocalStoragePath(),
                        getLocalDirectoryName(dataFlowDelete.getConsentRequestId()),
                        getLocalDirectoryName(transactionId));
                localDataStore.deleteExpiredConsentData(pathToTransactionDirectory);
            }
        };

        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    @SneakyThrows
    private DataFlowDelete convertToDataFlowDelete(byte[] message) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper.readValue(message, DataFlowDelete.class);
    }

    @SneakyThrows
    private String getLocalDirectoryName(String directoryName) {
        return String.format("%s", TokenUtils.encode(directoryName));
    }
}
