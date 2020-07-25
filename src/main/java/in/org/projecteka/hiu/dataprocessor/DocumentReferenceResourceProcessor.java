package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
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
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
            //if contained within a composition like discharge summary
            return;
        }
        DocumentReference docRef = (DocumentReference) resource;
        List<DocumentReference.DocumentReferenceContentComponent> contents = docRef.getContent();
        for (DocumentReference.DocumentReferenceContentComponent content : contents) {
            if (content.hasAttachment()) {
                new AttachmentDataTypeProcessor().process(content.getAttachment(), dataContext.getLocalStoragePath());
            }
        }
        bundleContext.doneProcessing(docRef);
        if (processContext != null) {
            //if processed as part of composition or other parent resource context, we do not need to track individual docReference
            //complication is - if its part of a composition (say snomed prescription record code), then it makes sense to be
            //displayed independently as well
            return;
        }
        String title = String.format("Clinical Document : %s", FHIRUtils.getDisplay(docRef.getType()));
        //NOTE: We are tracking clinical documents by date as well, even if referenced from DischargeSummary
        bundleContext.trackResource(ResourceType.DocumentReference, docRef.getId(), docRef.getDate(), title);
    }
}
