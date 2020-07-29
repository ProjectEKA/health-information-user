package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.DataFlowProperties;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.common.RabbitQueueNames;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.GatewayDataFlowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequest;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static reactor.core.publisher.Mono.empty;

class DataFlowRequestListenerTest {
    @Mock
    private MessageListenerContainerFactory messageListenerContainerFactory;

    @Mock
    private MessageListenerContainer messageListenerContainer;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DestinationsConfig destinationsConfig;

    @Mock
    private DestinationsConfig.DestinationInfo destinationInfo;

    @Mock
    private DataFlowClient dataFlowClient;

    @Mock
    private DataFlowRepository dataFlowRepository;

    @Mock
    private DataFlowProperties dataFlowProperties;

    @Mock
    private Gateway gateway;

    @Mock
    private CacheAdapter<String, DataFlowRequestKeyMaterial> dataFlowCache;

    @Mock
    private ConsentRepository consentRepository;

    private DataFlowRequestListener dataFlowRequestListener;
    private RabbitQueueNames queueNames;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        var decryptor = new Decryptor();
        queueNames = new RabbitQueueNames("");
        dataFlowRequestListener = new DataFlowRequestListener(messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient,
                dataFlowRepository,
                decryptor,
                dataFlowProperties,
                gateway,
                dataFlowCache,
                consentRepository,
                queueNames);
    }

    private byte[] convertToByteArray(DataFlowRequest dataFlowRequest) throws JsonProcessingException {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .writeValueAsBytes(dataFlowRequest);
    }

    @Test
    void shouldInitiateDataFlowRequestAndPutInCache() throws JsonProcessingException {
        var dataFlowRequest = dataFlowRequest().build();
        var dataFlowRequestBytes = convertToByteArray(dataFlowRequest);
        var messageListenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        var mockMessage = Mockito.mock(Message.class);
        when(destinationsConfig.getQueues().get(queueNames.getDataFlowRequestQueue())).thenReturn(destinationInfo);
        when(messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey())).thenReturn(messageListenerContainer);
        doNothing().when(messageListenerContainer).setupMessageListener(messageListenerCaptor.capture());
        when(mockMessage.getBody()).thenReturn(dataFlowRequestBytes);
        when(dataFlowProperties.isUsingGateway()).thenReturn(true);
        when(mockMessage.getBody()).thenReturn(dataFlowRequestBytes);
        when(gateway.token()).thenReturn(Mono.just("temp"));
        when(dataFlowClient.initiateDataFlowRequest(any(GatewayDataFlowRequest.class),anyString(),anyString()))
                .thenReturn(empty());
        when(consentRepository.getPatientId(anyString())).thenReturn(Mono.just("temp@ncg"));
        when(dataFlowRepository.addDataFlowRequest(anyString(),anyString(),any())).thenReturn(empty());
        when(dataFlowCache.put(any(), any())).thenReturn(empty());

        dataFlowRequestListener.subscribe();
        verify(messageListenerContainer,times(1)).start();
        verify(messageListenerContainer,times(1))
                .setupMessageListener(messageListenerCaptor.capture());

        MessageListener messageListener = messageListenerCaptor.getValue();

        messageListener.onMessage(mockMessage);
        verify(dataFlowClient,times(1)).initiateDataFlowRequest(any(),any(),any());
        verify(dataFlowRepository,times(1)).addDataFlowRequest(anyString(),anyString(),any());
    }
}
