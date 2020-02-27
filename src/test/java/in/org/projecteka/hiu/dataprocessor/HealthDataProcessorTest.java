package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HealthDataProcessorTest {
    @Mock
    private HealthDataRepository healthDataRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDeserializeDataNotificationRequestFromFile() throws IOException {
        //Path filePath = Paths.get("src","test","resources", "sample_data_flow_notification.json");
        Path filePath = Paths.get("src", "test", "resources", "Transaction123456.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        HealthDataProcessor processor = new HealthDataProcessor(healthDataRepository);
        String transactionId = "123456";
        DataAvailableMessage message = new DataAvailableMessage(transactionId, absolutePath, "1");
        processor.process(message);

        verify(healthDataRepository, times(1)).insertHealthData(eq(transactionId), eq("1"), any());
    }

}