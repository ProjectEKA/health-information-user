package in.org.projecteka.hiu.dataprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static in.org.projecteka.hiu.dataflow.TestBuilders.dataFlowRequestKeyMaterial;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthDataProcessorTest {
    @Mock
    private HealthDataRepository healthDataRepository;

    @Mock
    private DataFlowRepository dataFlowRepository;

    @Mock
    private Decryptor decryptor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDeserializeDataNotificationRequestFromFile() throws Exception {
        //Path filePath = Paths.get("src","test","resources", "sample_data_flow_notification.json");

        Path filePath = Paths.get("src", "test", "resources", "Transaction123456.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        HealthDataProcessor processor = new HealthDataProcessor(healthDataRepository, dataFlowRepository, decryptor);
        String transactionId = "123456";
        DataAvailableMessage message = new DataAvailableMessage(transactionId, absolutePath, "1");
        var content = getFHIRResource(message).getNotifiedData().getEntries().get(0).getContent();
        var savedKeyMaterial = dataFlowRequestKeyMaterial().build();

        when(healthDataRepository.insertHealthData(eq(transactionId), eq("1"), any())).thenReturn(Mono.empty());
        when(dataFlowRepository.getKeys("123456")).thenReturn(Mono.just(savedKeyMaterial));
        when(decryptor.decrypt(any(), any(), any())).thenReturn(content);
        processor.process(message);

        verify(healthDataRepository, times(1)).insertHealthData(eq(transactionId), eq("1"), any());
    }

    private DataContext getFHIRResource(DataAvailableMessage message) {
        Path dataFilePath = Paths.get(message.getPathToFile());
        try (InputStream inputStream = Files.newInputStream(dataFilePath)) {
            ObjectMapper objectMapper = new ObjectMapper();
            DataNotificationRequest dataNotificationRequest = objectMapper.readValue(inputStream,
                    DataNotificationRequest.class);
            return DataContext.builder()
                    .notifiedData(dataNotificationRequest)
                    .dataFilePath(dataFilePath)
                    .dataPartNumber(message.getPartNumber())
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}