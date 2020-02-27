package in.org.projecteka.hiu.dataprocessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
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

    private FhirContext fhirContext = FhirContext.forR4();

    private List<HITypeResourceProcessor> resourceProcessors = new ArrayList<>() {
        {
            add(new DiagnosticReportResourceProcessor());
        }
    };


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
        context.getNotifiedData().getEntries().stream().forEach( entry -> {
            if (hasContent(entry)) {
                List<String> errors = processEntryContent(entry, context);
//                if (!errors.isEmpty()) {
//                    //TODO: store the content? with errors, and state = BAD content?
//                } else {
//
//                }
            } else {
                //TODO: should download the content and essentially call processEntryContent(
            }
        });
    }

    private DataContext createContext(DataAvailableMessage message) {
        Path dataFilePath = Paths.get(message.getPathToFile());
        try (InputStream inputStream = Files.newInputStream(dataFilePath)) {
            ObjectMapper objectMapper = new ObjectMapper();
            DataNotificationRequest dataNotificationRequest = objectMapper.readValue(inputStream, DataNotificationRequest.class);
            return DataContext.builder()
                    .notifiedData(dataNotificationRequest)
                    .dataFilePath(dataFilePath)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }



    private List<String> processEntryContent(Entry entry, DataContext context) {
        List<String> errors = new ArrayList<>();
        IParser parser = getEntryParser(entry.getMedia());
        if (parser == null) {
            errors.add("Can't process entry content. Unknown media type.");
            return errors;
        }

        Bundle bundle = (Bundle) parser.parseResource(entry.getContent());
        if (!isValidBundleType(bundle.getType())) {
            errors.add("Can not process entry content. Entry content is not a FHIR Bundle type COLLECTION or DOCUMENT");
            return errors;
        }

        bundle.getEntry().stream().forEach(bundleEntry -> {
            ResourceType resourceType = bundleEntry.getResource().getResourceType();
            System.out.println("bundle entry resource type:  " + resourceType);
            HITypeResourceProcessor processor = identifyResourceProcessor(resourceType);
            if (processor != null) {
                processor.process(bundleEntry.getResource(), context);
            }
        });
        return errors;
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
