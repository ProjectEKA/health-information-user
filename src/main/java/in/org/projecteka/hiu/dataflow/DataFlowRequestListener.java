package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.DataFlowProperties;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.common.TraceableMessage;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyStructure;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static in.org.projecteka.hiu.ClientError.queueNotFound;
import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;
import static in.org.projecteka.hiu.common.Serializer.to;
import static reactor.core.publisher.Mono.defer;

@AllArgsConstructor
public class DataFlowRequestListener {
    private static final Logger logger = LoggerFactory.getLogger(DataFlowRequestListener.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final DataFlowClient dataFlowClient;
    private final DataFlowRepository dataFlowRepository;
    private final Decryptor decryptor;
    private final DataFlowProperties dataFlowProperties;
    private final Gateway gateway;
    private final CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCache;
    private final ConsentRepository consentRepository;
    private final RabbitQueueNames queueNames;

    @PostConstruct
    @SneakyThrows
    public void subscribe() {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(queueNames.getDataFlowRequestQueue());
        if (destinationInfo == null) {
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            var traceableMessage = to(message.getBody(), TraceableMessage.class);
            DataFlowRequest dataFlowRequest = convertToDataFlowRequest((traceableMessage.get().getMessage()));
            String correlationId = traceableMessage.get().getCorrelationId();
            String consentId = dataFlowRequest.getConsent().getId();

            MDC.put(Constants.CORRELATION_ID, correlationId);
            logger.info("Received data flow request with consent id : {}", consentId);
            try {
                var dataRequestKeyMaterial = dataFlowRequestKeyMaterial();
                var keyMaterial = keyMaterial(dataRequestKeyMaterial);
                dataFlowRequest.setKeyMaterial(keyMaterial);

                gateway.token()
                        .flatMap(token -> {
                            var gatewayDataFlowRequest = getDataFlowRequest(dataFlowRequest);
                            String requestId = gatewayDataFlowRequest.getRequestId().toString();
                            logger.info("[DataFlowRequestListener] Initiating data flow request to consent manager with RequestID" +
                                    " {}", requestId);
                            return consentRepository.getPatientId(consentId)
                                    .flatMap(patientId -> dataFlowClient.initiateDataFlowRequest(gatewayDataFlowRequest,
                                            token,
                                            getCmSuffix(patientId)))
                                    .then(defer(() -> dataFlowRepository.addDataFlowRequest(requestId,
                                            consentId,
                                            dataFlowRequest)))
                                    .then(defer(() -> dataFlowCache.put(requestId, dataRequestKeyMaterial)));
                        }).subscriberContext(ctx -> {
                    Optional<String> traceId = Optional.ofNullable(MDC.get(CORRELATION_ID));
                    return traceId.map(id -> ctx.put(CORRELATION_ID, id))
                            .orElseGet(() -> ctx.put(CORRELATION_ID, UUID.randomUUID().toString()));
                }).block();
                MDC.clear();
            } catch (Exception exception) {
                // TODO: Put the message in dead letter queue
                logger.error("Exception on key creation {exception}", exception);
                throw new AmqpRejectAndDontRequeueException(exception);
            }
        };

        mlc.setupMessageListener(messageListener);

        mlc.start();
    }


    private GatewayDataFlowRequest getDataFlowRequest(DataFlowRequest dataFlowRequest) {
        var requestId = UUID.randomUUID();
        var timestamp = LocalDateTime.now(ZoneOffset.UTC);
        return new GatewayDataFlowRequest(requestId, timestamp, dataFlowRequest);
    }

    private String getCmSuffix(String patientId) {
        return patientId.split("@")[1];
    }

    private DataFlowRequestKeyMaterial dataFlowRequestKeyMaterial() throws Exception {
        var keyPair = decryptor.generateKeyPair();
        var privateKey = decryptor.getBase64String(decryptor.getEncodedPrivateKey(keyPair.getPrivate()));
        var publicKey = decryptor.getBase64String(decryptor.getEncodedPublicKey(keyPair.getPublic()));
        return DataFlowRequestKeyMaterial.builder()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .randomKey(decryptor.generateRandomKey())
                .build();
    }

    private KeyMaterial keyMaterial(DataFlowRequestKeyMaterial dataFlowKeyMaterial) {
        logger.info("Creating KeyMaterials");
        return KeyMaterial.builder()
                .cryptoAlg(Decryptor.ALGORITHM)
                .curve(Decryptor.CURVE)
                .dhPublicKey(KeyStructure.builder()
                        .expiry(getExpiryDate())
                        .keyValue(dataFlowKeyMaterial.getPublicKey())
                        .parameters(Decryptor.EH_PUBLIC_KEY_PARAMETER)
                        .build())
                .nonce(dataFlowKeyMaterial.getRandomKey())
                .build();
    }

    private LocalDateTime getExpiryDate() {
        return LocalDateTime.now().plusDays(dataFlowProperties.getOffsetInDays());
    }

    @SneakyThrows
    private DataFlowRequest convertToDataFlowRequest(Object message) {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.convertValue(message, DataFlowRequest.class);
    }
}
