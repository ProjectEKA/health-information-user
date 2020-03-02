package in.org.projecteka.hiu.dataprocessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessedResource;
import org.apache.log4j.Logger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HealthDataProcessor {
    public static final String MEDIA_APPLICATION_FHIR_JSON = "application/fhir+json";
    public static final String MEDIA_APPLICATION_FHIR_XML = "application/fhir+xml";
    private HealthDataRepository healthDataRepository;
    private DataFlowRepository dataFlowRepository;
    private Decryptor decryptor;
    private FhirContext fhirContext = FhirContext.forR4();
    private static final Logger logger = Logger.getLogger(HealthDataProcessor.class);

    private List<HITypeResourceProcessor> resourceProcessors = new ArrayList<>() {
        {
            add(new DiagnosticReportResourceProcessor());
        }
    };

    public HealthDataProcessor(HealthDataRepository healthDataRepository, DataFlowRepository dataFlowRepository, Decryptor decryptor) {
        this.healthDataRepository = healthDataRepository;
        this.dataFlowRepository = dataFlowRepository;
        this.decryptor = decryptor;
    }

    public void registerHITypeResourceHandler(HITypeResourceProcessor resourceProcessor) {
        resourceProcessors.add(resourceProcessor);
    }

    public void process(DataAvailableMessage message) throws IOException {
        DataContext context = createContext(message);
        if (context.getNotifiedData() != null) {
            processEntries(context);
        } else {
            // TODO: this should never happen, unless someone sends empty response.
            // TODO: Noop. this can be a valid scenario.
        }
    }

    private void processEntries(DataContext context) {
        DataFlowRequestKeyMaterial keyMaterial = dataFlowRepository.getKeys(context.getTransactionId()).block();

        context.getNotifiedData().getEntries().stream().forEach(entry -> {
            if (hasContent(entry)) {
                ProcessedResource processedResource = processEntryContent(context, entry, keyMaterial);
                if (!processedResource.hasErrors()) {
                    String resource =
                            getEntryParser(entry.getMedia()).encodeResourceToString(processedResource.getResource());
                    healthDataRepository.insertHealthData(
                            context.getTransactionId(),
                            context.getDataPartNumber(),
                            resource)
                            .block();
                }
                //TODO: if the above has errors, store the content? with errors, and state = BAD content?s
            }
            //TODO: else part. download the content from entry.getLink().getHref(), and essentially call processEntryContent()
        });
    }

    private DataContext createContext(DataAvailableMessage message) {
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


    private ProcessedResource processEntryContent(DataContext context, Entry entry, DataFlowRequestKeyMaterial keyMaterial) {
        ProcessedResource result = new ProcessedResource();
        List<String> errors = new ArrayList<>();
        IParser parser = getEntryParser(entry.getMedia());
        if (parser == null) {
            result.addError("Can't process entry content. Unknown media type.");
            return result;
        }
        String decryptedContent = null;
        try {
            decryptedContent = decryptor.decrypt(context.getKeyMaterial(),
                    keyMaterial,
                    entry.getContent());
        } catch (Exception e) {
            logger.error("Error while decrypting {exception}", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        Bundle bundle = (Bundle) parser.parseResource(decryptedContent);
        if (!isValidBundleType(bundle.getType())) {
            result.addError("Can not process entry content. Entry content is not a FHIR Bundle type COLLECTION or " +
                    "DOCUMENT");
            return result;
        }

        bundle.getEntry().forEach(bundleEntry -> {
            ResourceType resourceType = bundleEntry.getResource().getResourceType();
            System.out.println("bundle entry resource type:  " + resourceType);
            HITypeResourceProcessor processor = identifyResourceProcessor(resourceType);
            if (processor != null) {
                processor.process(bundleEntry.getResource(), context);
            }
        });
        result.addResource(bundle);
        return result;
    }

    private HITypeResourceProcessor identifyResourceProcessor(ResourceType resourceType) {
        return resourceProcessors.stream().filter(p -> p.supports(resourceType)).findAny().orElse(null);
    }

    private boolean isValidBundleType(Bundle.BundleType type) {
        return type.equals(Bundle.BundleType.COLLECTION) || type.equals(Bundle.BundleType.DOCUMENT);
    }

    private IParser getEntryParser(String media) {
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_JSON)) {
            return fhirContext.newJsonParser();
        }
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_XML)) {
            return fhirContext.newXmlParser();
        }
        return null;
    }


    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }
}
