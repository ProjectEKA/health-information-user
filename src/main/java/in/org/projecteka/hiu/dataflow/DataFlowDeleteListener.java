package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
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

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_DELETE_QUEUE;

@AllArgsConstructor
public class DataFlowDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(DataFlowDeleteListener.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final DataFlowRepository dataFlowRepository;
    private final HealthInformationRepository healthInformationRepository;
    private final DataFlowServiceProperties dataFlowServiceProperties;
    private final LocalDataStore localDataStore;

    @PostConstruct
    @SneakyThrows
    public void subscribe() {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(DATA_FLOW_DELETE_QUEUE);
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
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(message, DataFlowDelete.class);
    }

    @SneakyThrows
    private String getLocalDirectoryName(String directoryName) {
        return String.format("%s", TokenUtils.encode(directoryName));
    }
}
