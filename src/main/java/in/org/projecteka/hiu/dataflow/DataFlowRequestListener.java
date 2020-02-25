package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.ClientError;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.consent.DataFlowRequestPublisher;
import in.org.projecteka.hiu.dataflow.cryptohelper.CryptoHelper;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyStructure;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

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
            try{
                var dataRequestKeyMaterial = dataFlowRequestKeyMaterial();
                var keyMaterial = keyMaterial(dataRequestKeyMaterial);
                dataFlowRequest.setKeyMaterial(keyMaterial);

                logger.info("Initiating data flow request to consent manager");
                dataFlowClient.initiateDataFlowRequest(dataFlowRequest)
                        .flatMap(dataFlowRequestResponse ->
                                dataFlowRepository.addDataRequest(dataFlowRequestResponse.getTransactionId(), dataFlowRequest)
                                        .then(dataFlowRepository.addKeys(
                                                dataFlowRequestResponse.getTransactionId(),
                                                dataRequestKeyMaterial)))
                        .block();
            } catch (Exception exception){
                // TODO: Put the message in dead letter queue
                logger.fatal("Exception on key creation {exception}", exception);
            }
        };

        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    private DataFlowRequestKeyMaterial dataFlowRequestKeyMaterial() throws Exception {
        var keyPair = CryptoHelper.generateKeyPair();
        var privateKey = CryptoHelper.getBase64String(CryptoHelper.savePrivateKey(keyPair.getPrivate()));
        var publicKey = CryptoHelper.getBase64String(CryptoHelper.savePublicKey(keyPair.getPublic()));
        var dataFlowKeyMaterial = DataFlowRequestKeyMaterial.builder()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .randomKey(CryptoHelper.generateRandomKey())
                .build();
        return dataFlowKeyMaterial;
    }

    private KeyMaterial keyMaterial(DataFlowRequestKeyMaterial dataFlowKeyMaterial) {
        logger.info("Creating KeyMaterials");
        return KeyMaterial.builder()
                .cryptoAlg(CryptoHelper.ALGORITHM)
                .curve(CryptoHelper.CURVE)
                .dhPublicKey(KeyStructure.builder()
                        .expiry("")
                        .keyValue(dataFlowKeyMaterial.getPublicKey())
                        .parameters("").build())
                .randomKey(dataFlowKeyMaterial.getRandomKey())
                .build();
    }

    @SneakyThrows
    private DataFlowRequest convertToDataFlowRequest(byte[] message) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(message, DataFlowRequest.class);
    }
}
