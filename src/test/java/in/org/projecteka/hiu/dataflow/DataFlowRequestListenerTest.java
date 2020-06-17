package in.org.projecteka.hiu.dataflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.Cache;
import in.org.projecteka.hiu.DataFlowProperties;
import in.org.projecteka.hiu.DataFlowRequestWithKeyMaterial;
import in.org.projecteka.hiu.DestinationsConfig;
import in.org.projecteka.hiu.MessageListenerContainerFactory;
import in.org.projecteka.hiu.common.CentralRegistry;
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

import java.util.UUID;

import static in.org.projecteka.hiu.HiuConfiguration.DATA_FLOW_REQUEST_QUEUE;
import static org.mockito.Mockito.*;

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
    private CentralRegistry centralRegistry;

    @Mock
    private Cache<String, DataFlowRequestKeyMaterial> dataFlowCache;

    @Mock
    private ConsentRepository consentRepository;

    private DataFlowRequestListener dataFlowRequestListener;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        var decryptor = new Decryptor();
        dataFlowRequestListener = new DataFlowRequestListener(messageListenerContainerFactory,
                destinationsConfig,
                dataFlowClient,
                dataFlowRepository,
                decryptor,
                dataFlowProperties,
                centralRegistry,
                dataFlowCache,
                consentRepository);
    }

    private byte[] convertToByteArray(DataFlowRequest dataFlowRequest) throws JsonProcessingException {

        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .writeValueAsBytes(dataFlowRequest);
    }

    @Test
    void shouldInitiateDataFlowRequestAndPutInCache() throws JsonProcessingException {
        var dataFlowRequest = TestBuilders.dataFlowRequest().build();
        var dataFlowRequestBytes = convertToByteArray(dataFlowRequest);
        var messageListenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        var mockMessage = Mockito.mock(Message.class);

        when(destinationsConfig.getQueues().get(DATA_FLOW_REQUEST_QUEUE)).thenReturn(destinationInfo);
        when(messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey())).thenReturn(messageListenerContainer);
        doNothing().when(messageListenerContainer).setupMessageListener(messageListenerCaptor.capture());
        when(mockMessage.getBody()).thenReturn(dataFlowRequestBytes);
        when(dataFlowProperties.isUsingGateway()).thenReturn(true);
        when(mockMessage.getBody()).thenReturn(dataFlowRequestBytes);
        when(centralRegistry.token()).thenReturn(Mono.just("temp"));
        when(dataFlowClient.initiateDataFlowRequest(any(GatewayDataFlowRequest.class),anyString(),anyString())).thenReturn(Mono.empty());
        when(consentRepository.getPatientId(anyString())).thenReturn(Mono.just("temp@ncg"));

        dataFlowRequestListener.subscribe();
        verify(messageListenerContainer,times(1)).start();
        verify(messageListenerContainer,times(1)).setupMessageListener(messageListenerCaptor.capture());

        MessageListener messageListener = messageListenerCaptor.getValue();
        messageListener.onMessage(mockMessage);
        verify(dataFlowClient,times(1)).initiateDataFlowRequest(any(),any(),any());
        verify(dataFlowClient,times(0)).initiateDataFlowRequest(any(),any());
    }
}