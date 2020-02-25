package in.org.projecteka.hiu.dataprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;

import java.util.HashMap;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_PROCESS_QUEUE;

@AllArgsConstructor
public class DataAvailabilityListener {
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;


    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);

    @PostConstruct
    @SneakyThrows
    public void subscribe()  {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(DATA_FLOW_PROCESS_QUEUE);
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            HashMap requestObject = deserializeMessage(message);
            String transactionId = (String) requestObject.get("transactionId");
            String pathToFile = (String) requestObject.get("pathToFile");
            logger.info(String.format("Received notification of data availability for transaction id : %s", transactionId));
            logger.info(String.format("Processing data from file : %s", pathToFile));
            try {
                new HealthDataProcessor().process(transactionId, pathToFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        mlc.setupMessageListener(messageListener);
        mlc.start();
    }

    @SneakyThrows
    private HashMap deserializeMessage(Message msg) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(msg.getBody(), HashMap.class);
    }
}
