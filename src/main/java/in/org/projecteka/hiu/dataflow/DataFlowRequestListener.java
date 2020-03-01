package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_REQUEST_QUEUE;


@AllArgsConstructor
public class DataFlowRequestListener {
    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);
    private MessageListenerContainerFactory messageListenerContainerFactory;
    private DestinationsConfig destinationsConfig;
    private DataFlowClient dataFlowClient;
    private DataFlowRepository dataFlowRepository;

    @PostConstruct
    @SneakyThrows
    public void subscribe() throws ClientError {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(DATA_FLOW_REQUEST_QUEUE);
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            DataFlowRequest dataFlowRequest = convertToDataFlowRequest(message.getBody());
            logger.info("Received data flow request with consent id : " + dataFlowRequest.getConsent().getId());
            logger.info("Initiating data flow request to consent manager");
            dataFlowClient.initiateDataFlowRequest(dataFlowRequest)
                    .flatMap(dataFlowRequestResponse ->
                            dataFlowRepository.addDataRequest(dataFlowRequestResponse.getTransactionId(),
                                    dataFlowRequest.getConsent().getId(), dataFlowRequest))
                    .block();
        };
        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    @SneakyThrows
    private DataFlowRequest convertToDataFlowRequest(byte[] message) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(message, DataFlowRequest.class);
    }
}
