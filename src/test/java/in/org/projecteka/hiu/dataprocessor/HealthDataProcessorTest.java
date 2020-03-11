package in.org.projecteka.hiu.dataprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.LocalDicomServerProperties;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import in.org.projecteka.hiu.dicomweb.OrthancDicomWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

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

    @AfterAll
    public static void cleanUp() throws IOException {
        /**
         * NOTE this would delete any files matching patterns under the test/resources directory.
         * That might include your test file (if you have put any pdf or dcm file.
         */
        deleteGeneratedFiles("pdf");
        deleteGeneratedFiles("dcm");
    }

    @Test
    public void shouldDeserializeDataNotificationRequestFromFile() throws Exception {
        Path filePath = Paths.get("src", "test", "resources", "Transaction123456.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        //TODO
        List<HITypeResourceProcessor> resourceProcessors = Collections.singletonList(
                new DiagnosticReportResourceProcessor(new OrthancDicomWebServer(new LocalDicomServerProperties())));
        HealthDataProcessor processor = new HealthDataProcessor(healthDataRepository, dataFlowRepository, decryptor, resourceProcessors);
        String transactionId = "123456";
        String partNumber = "1";
        DataAvailableMessage message = new DataAvailableMessage(transactionId, absolutePath, partNumber);
        var content = getFHIRResource(message).getNotifiedData().getEntries().get(0).getContent();
        var savedKeyMaterial = dataFlowRequestKeyMaterial().build();

        when(healthDataRepository.insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED)))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.getKeys("123456")).thenReturn(Mono.just(savedKeyMaterial));
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber,"", HealthInfoStatus.PROCESSING))
                .thenReturn(Mono.empty());
        when(decryptor.decrypt(any(), any(), any())).thenReturn(content);
        processor.process(message);

        verify(healthDataRepository, times(1))
                .insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED));
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED);
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.PROCESSING);
    }

    @Test
    public void shouldDownloadFileFromUrlInPresentedForm() throws Exception {
        Path filePath = Paths.get("src", "test", "resources", "Transaction789.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        List<HITypeResourceProcessor> resourceProcessors = Collections.singletonList(
                new DiagnosticReportResourceProcessor(new OrthancDicomWebServer(new LocalDicomServerProperties())));
        HealthDataProcessor processor = new HealthDataProcessor(healthDataRepository,
                dataFlowRepository,
                decryptor,
                resourceProcessors);
        String transactionId = "123456";
        String partNumber = "1";
        DataAvailableMessage message = new DataAvailableMessage(transactionId, absolutePath, partNumber);
        var content = getFHIRResource(message).getNotifiedData().getEntries().get(0).getContent();
        var savedKeyMaterial = dataFlowRequestKeyMaterial().build();

        when(healthDataRepository.insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED)))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.getKeys("123456")).thenReturn(Mono.just(savedKeyMaterial));
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber,"", HealthInfoStatus.PROCESSING))
                .thenReturn(Mono.empty());
        when(decryptor.decrypt(any(), any(), any())).thenReturn(content);
        processor.process(message);

        verify(healthDataRepository, times(1))
                .insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED));
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED);
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.PROCESSING);
    }

    @Test
    public void shouldDownloadFileFromUrlInMedia() throws Exception {
        Path filePath = Paths.get("src", "test", "resources", "Transaction567.json");
        String absolutePath = filePath.toFile().getAbsolutePath();
        List<HITypeResourceProcessor> resourceProcessors = Collections.singletonList(
                new DiagnosticReportResourceProcessor(new OrthancDicomWebServer(new LocalDicomServerProperties())));
        HealthDataProcessor processor = new HealthDataProcessor(healthDataRepository,
                dataFlowRepository,
                decryptor,
                resourceProcessors);
        String transactionId = "123456";
        String partNumber = "1";
        DataAvailableMessage message = new DataAvailableMessage(transactionId, absolutePath, partNumber);
        var content = getFHIRResource(message).getNotifiedData().getEntries().get(0).getContent();
        var savedKeyMaterial = dataFlowRequestKeyMaterial().build();

        when(healthDataRepository.insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED)))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.getKeys("123456")).thenReturn(Mono.just(savedKeyMaterial));
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED))
                .thenReturn(Mono.empty());
        when(dataFlowRepository.updateDataFlowWithStatus(transactionId, partNumber,"", HealthInfoStatus.PROCESSING))
                .thenReturn(Mono.empty());
        when(decryptor.decrypt(any(), any(), any())).thenReturn(content);
        processor.process(message);

        verify(healthDataRepository, times(1))
                .insertHealthData(eq(transactionId), eq(partNumber), any(), eq(EntryStatus.SUCCEEDED));
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.SUCCEEDED);
        verify(dataFlowRepository, times(1))
                .updateDataFlowWithStatus(transactionId, partNumber, "", HealthInfoStatus.PROCESSING);
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


    public static void deleteGeneratedFiles(String extension) throws IOException {
        Path filePath = Paths.get("src", "test", "resources");
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(String.format("glob:**.{%s}",extension));
        Files.walk(filePath).filter(pathMatcher::matches).forEach(f -> {
            try {
                System.out.println("deleting file: " + f);
                Files.delete(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}