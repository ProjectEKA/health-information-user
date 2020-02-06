package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import javax.annotation.PostConstruct;

import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_REQUEST_QUEUE;


@AllArgsConstructor
public class DataFlowRequestListener {
    private MessageListenerContainerFactory messageListenerContainerFactory;
    private DestinationsConfig destinationsConfig;
    private Jackson2JsonMessageConverter converter;
    private DataFlowClient dataFlowClient;
    private DataFlowRequestRepository dataFlowRequestRepository;

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

            DataFlowRequest.builder().build();
            dataFlowClient.initiateDataFlowRequest(dataFlowRequest)
                    .flatMap(dataFlowRequestResponse ->
                            dataFlowRequestRepository.addDataFlowRequest(
                                    dataFlowRequestResponse.getTransactionId(),
                                    dataFlowRequest))
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
