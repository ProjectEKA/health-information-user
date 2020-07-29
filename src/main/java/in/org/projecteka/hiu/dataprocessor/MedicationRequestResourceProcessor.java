package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class MedicationRequestResourceProcessor implements HITypeResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(MedicationRequestResourceProcessor.class);
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.MedicationRequest);
    }

    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
             //if contained within a composition like discharge summary
            return;
        }
        bundleContext.doneProcessing(resource);
        if (processContext != null) {
            //if processed as part of composition or other parent resource context, we do not need to track individual medicationRequest
            return;
        }
        MedicationRequest medicationRequest = (MedicationRequest) resource;
        processReasonReferences(medicationRequest, dataContext, bundleContext, processContext);
        Date date = getPrescribedDate(medicationRequest, bundleContext, null);
        String title = String.format("Medication Request : %s", getMedicationDisplay(medicationRequest));
        bundleContext.trackResource(ResourceType.MedicationRequest, medicationRequest.getId(), date, title);
    }

    private void processReasonReferences(MedicationRequest medicationRequest, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (medicationRequest.hasReasonReference()) {
            for (Reference reference : medicationRequest.getReasonReference()) {
                ProcessContext requestCtx = processContext != null ? processContext : getMedRequestContext(medicationRequest, bundleContext);
                IBaseResource resource = reference.getResource();
                if (resource == null) {
                    logger.warn(String.format("Medication reference not found. diagnosticReport id: %s, result reference: %s",
                            medicationRequest.getId(), reference.getReference()));
                }
                if (!(resource instanceof Resource)) {
                    return;
                }
                Resource bundleResource = (Resource) resource;
                HITypeResourceProcessor resProcessor = bundleContext.findResourceProcessor(bundleResource.getResourceType());
                if (resProcessor != null) {
                    resProcessor.process(bundleResource, dataContext, bundleContext, requestCtx);
                }
            }
        }
    }

    private ProcessContext getMedRequestContext(MedicationRequest request, BundleContext bundleContext) {
        return new ProcessContext(
                () -> getPrescribedDate(request, bundleContext, null),
                request::getId,
                this::getMedicationRequestResourceType);
    }

    private ResourceType getMedicationRequestResourceType() {
        return ResourceType.MedicationRequest;
    }

    private String getMedicationDisplay(MedicationRequest medicationRequest) {
        if (medicationRequest.hasMedicationCodeableConcept()) {
            return FHIRUtils.getDisplay(medicationRequest.getMedicationCodeableConcept());
        } else if (medicationRequest.hasMedicationReference()) {
            IBaseResource resource = medicationRequest.getMedicationReference().getResource();
            if (resource != null && resource instanceof Medication) {
                return FHIRUtils.getDisplay(((Medication) resource).getCode());
            }
        }
        return Constants.EMPTY_STRING;
    }

    /**
     * MedicationRequest.authoredOn is a non-mandatory field.
     * The problem here is that if a MedicationRequest is done in an encounter context,
     * then it might not have the date (E.g. a composition for a day-care). In such a case,
     * the date would be same as composition date. This is quite unlikely though, unless for discharge summary in inpatient context.
     * Unless we mandate so. In such case just throw an error if authoredOn is null.
     * Right now falling back on bundle or processContext date
     */
    private Date getPrescribedDate(MedicationRequest medicationRequest, BundleContext bundleContext, ProcessContext processContext) {
        Date date = medicationRequest.getAuthoredOn();
        if (date == null && processContext != null) {
            date = processContext.getContextDate();
        }
        if (date == null) {
            date = bundleContext.getBundleDate();
        }
        return date;
    }
}
