package in.org.projecteka.hiu.dataprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.LocalDicomServerProperties;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.clients.HealthInformationClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dicomweb.OrthancDicomWebServer;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_PROCESS_QUEUE;

@AllArgsConstructor
public class DataAvailabilityListener {
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final HealthDataRepository healthDataRepository;
    private final DataFlowRepository dataFlowRepository;
    private final LocalDicomServerProperties dicomServerProperties;
    private final HealthInformationClient healthInformationClient;
    private final CentralRegistry centralRegistry;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;

    private static final Logger logger = Logger.getLogger(DataFlowRequestPublisher.class);

    @PostConstruct
    @SneakyThrows
    public void subscribe() {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(DATA_FLOW_PROCESS_QUEUE);
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            DataAvailableMessage dataAvailableMessage = deserializeMessage(message);
            logger.info(String.format("Received notification of data availability for transaction id : %s",
                    dataAvailableMessage.getTransactionId()));
            logger.info(String.format("Processing data from file : %s", dataAvailableMessage.getPathToFile()));
            try {
                HealthDataProcessor healthDataProcessor = new HealthDataProcessor(
                        healthDataRepository,
                        dataFlowRepository,
                        new Decryptor(),
                        allResourceProcessors(),
                        healthInformationClient,
                        centralRegistry,
                        hiuProperties,
                        consentRepository);
                healthDataProcessor.process(dataAvailableMessage);
            } catch (Exception exception) {
                logger.error(exception);
                throw new AmqpRejectAndDontRequeueException(exception);
            }
        };
        mlc.setupMessageListener(messageListener);
        mlc.start();
    }

    private List<HITypeResourceProcessor> allResourceProcessors() {
        return Collections.singletonList(new DiagnosticReportResourceProcessor(new OrthancDicomWebServer(dicomServerProperties)));
    }

    @SneakyThrows
    private DataAvailableMessage deserializeMessage(Message msg) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper.readValue(msg.getBody(), DataAvailableMessage.class);
    }
}
