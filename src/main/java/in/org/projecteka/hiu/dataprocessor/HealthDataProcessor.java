package in.org.projecteka.hiu.dataprocessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import org.hl7.fhir.r4.model.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HealthDataProcessor {
    public void process(String transactionId, String pathToFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(pathToFile))) {
            ObjectMapper objectMapper = new ObjectMapper();
            DataNotificationRequest dataNotificationRequest = objectMapper.readValue(inputStream, DataNotificationRequest.class);
            if (dataNotificationRequest != null) {
                processEntries(dataNotificationRequest);
            } else {
                //TODO: this should never happen
            }
        }
    }

    private void processEntries(DataNotificationRequest dataNotificationRequest) {
        dataNotificationRequest.getEntries().stream().forEach( entry -> {
            if (hasContent(entry)) {
                Bundle bundle = toFhirBundle(entry);
                //TODO validate bundle.getType().name to be either collection or document.
                System.out.println("bundle type: " + bundle.getType());
                System.out.println("bundle id:" + bundle.getId());
                bundle.getEntry().stream().forEach(bec -> {
                    System.out.println("bundle entry resource type:  " + bec.getResource().getResourceType());
                });


            }
        });
    }

    private Bundle toFhirBundle(Entry entry) {
        FhirContext fhirContext = FhirContext.forR4();
        IParser iParser = fhirContext.newJsonParser();
        return (Bundle) iParser.parseResource(entry.getContent());
    }

    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }
}
