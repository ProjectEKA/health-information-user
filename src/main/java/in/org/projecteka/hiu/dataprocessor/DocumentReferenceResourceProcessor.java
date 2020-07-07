package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DocumentReferenceResourceProcessor implements HITypeResourceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentReferenceResourceProcessor.class);
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.DocumentReference);
    }

    @Override
    public void process(Resource resource, DataContext context) {
        DocumentReference docRef = (DocumentReference) resource;
        List<DocumentReference.DocumentReferenceContentComponent> contents = docRef.getContent();
        for (DocumentReference.DocumentReferenceContentComponent content : contents) {
            if (content.hasAttachment()) {
                new AttachmentDataTypeProcessor().process(content.getAttachment(), context.getLocalStoragePath());
            }
        }
    }
}
