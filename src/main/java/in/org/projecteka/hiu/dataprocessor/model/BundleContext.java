package in.org.projecteka.hiu.dataprocessor.model;

import in.org.projecteka.hiu.dataprocessor.HITypeResourceProcessor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

public class BundleContext {
    private Bundle bundle;
    private Function<ResourceType, HITypeResourceProcessor> resourceProcessor;
    private List<Resource> processedResList = new ArrayList<>();
    private List<TrackedResourceReference> trackedResources = new ArrayList<>();

    public Date getBundleDate() {
        return bundle.getTimestamp();
    }

    public BundleContext(Bundle bundle, Function<ResourceType, HITypeResourceProcessor> resourceProcessor) {
        this.bundle = bundle;
        this.resourceProcessor = resourceProcessor;
    }

    public boolean isProcessed(Resource resource) {
        return processedResList.contains(resource);
    }

    /**
     * Keeps track of Resources that are already processed in the currently processing bundle;
     * primarily to track the top level resources that are included in the same bundle
     * and which are linked as reference to other resources.
     * For example - DiagnosticReport.media, Composition.section.entry.
     * Note, HIU does not process the typical referenced resources like Practitioner or Medication (in MedicationRequest),
     * as HIU expects that such referenced resources are to be resolved by the consumer of the data.
     * The intent is to ensure that resources in the bundle are not double processed by
     * the {@link in.org.projecteka.hiu.dataprocessor.HITypeResourceProcessor}. For example,
     * if a DocumentReference is already processed than it need to be processed again if associated with a Composition.
     * @param resource FHIR resource that is already processed by a HITypeResourceProcessor
     */
    public void doneProcessing(Resource resource) {
        processedResList.add(resource);
    }

    public void trackResource(ResourceType resourceType, String resourceId, Date date, String title) {
        LocalDateTime localDateTime = (date != null) ?
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
        trackedResources.add(new TrackedResourceReference(resourceType, resourceId, localDateTime, title));
    }

    public HITypeResourceProcessor findResourceProcessor(ResourceType resourceType) {
        return resourceProcessor.apply(resourceType);
    }

    public List<TrackedResourceReference> getTrackedResources() {
        return trackedResources;
    }
}
