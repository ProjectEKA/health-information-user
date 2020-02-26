package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class HealthDataProcessorTest {

    @Test
    public void shouldDeserializeDataNotificationRequestFromFile() throws IOException {
        //Path filePath = Paths.get("src","test","resources", "sample_data_flow_notification.json");
        Path filePath = Paths.get("src","test","resources", "Transaction123456.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        HealthDataProcessor processor = new HealthDataProcessor();
        DataAvailableMessage message = new DataAvailableMessage("123456", absolutePath);
        processor.process(message);
    }

}