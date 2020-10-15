package in.org.projecteka.hiu.dataprocessor.model;

import in.org.projecteka.hiu.dataprocessor.FHIRUtils;
import in.org.projecteka.hiu.dataprocessor.HITypeResourceProcessor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BundleContext {
    private Bundle bundle;
    private Function<ResourceType, HITypeResourceProcessor> resourceProcessor;
    private List<Resource> processedResList = new ArrayList<>();
    private List<TrackedResourceReference> trackedResources = new ArrayList<>();

    private static final String VERSION_UNKNOWN = "UNKNOWN";

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

    public String getBundleUniqueId() {
        String version = getBundleVersion(bundle);
        return String.format("%s.%s",bundle.getId(), version);
    }

    private String getBundleVersion(Bundle bundle) {
        if (!bundle.hasMeta()) {
            return VERSION_UNKNOWN;
        }
        return bundle.getMeta().getVersionId() != null ? bundle.getMeta().getVersionId() : VERSION_UNKNOWN;
    }

    public String getDocumentType() {
        Optional<Bundle.BundleEntryComponent> entryComponent
                = bundle.getEntry().stream().filter(e ->
                e.getResource().getResourceType().equals(ResourceType.Composition)).findFirst();
        if (entryComponent.isEmpty()) {
            return "";
        }
        Composition composition = (Composition) entryComponent.get().getResource();
        CodeableConcept concept = composition.getType();
        Optional<String> hiType = concept.getCoding().stream().map(c -> FHIRUtils.getHiTypeForCode(c.getCode()))
                .filter(type -> !"".equals(type))
                .findFirst();
        return hiType.orElse("");
    }

    public List<Organization> getOrigins() {
        Optional<Bundle.BundleEntryComponent> entryComponent
                = bundle.getEntry().stream().filter(e ->
                e.getResource().getResourceType().equals(ResourceType.Composition)).findFirst();
        if (entryComponent.isEmpty()) {
            return Collections.emptyList();
        }
        Composition composition = (Composition) entryComponent.get().getResource();
        List<Organization> organizations = identifyOrgFromAttester(composition);
        if (organizations.isEmpty()) {
            organizations = identifyOrgFromAuthor(composition);
        }
        return organizations;
    }

    private List<Organization> identifyOrgFromAttester(Composition composition) {
        if (!composition.hasAttester()) {
            return Collections.emptyList();
        }
        List<Organization> organizations = composition.getAttester().stream().filter(attesterRef -> {
            if (!attesterRef.hasParty()) {
                return false;
            }
            IBaseResource resource = attesterRef.getParty().getResource();
            return resource != null && resource instanceof Organization;
        }).map(ref -> (Organization) ref.getParty().getResource()).collect(Collectors.toList());
        return organizations;
    }

    private List<Organization> identifyOrgFromAuthor(Composition composition) {
        if (!composition.hasAuthor()) {
            return Collections.emptyList();
        }
        List<Organization> organizations = composition.getAuthor().stream().filter(authorRef -> {
            IBaseResource resource = authorRef.getResource();
            return resource != null && resource instanceof Organization;
        }).map(authorRef -> (Organization) authorRef.getResource()).collect(Collectors.toList());
        return organizations;
    }
}
