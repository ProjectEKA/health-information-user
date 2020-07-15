package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.Date;

public class MedicationRequestResourceProcessor implements HITypeResourceProcessor {
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
        Date date = getPrescribedDate(medicationRequest, bundleContext, null);
        String title = String.format("Prescribed Medication : %s", getMedicationDisplay(medicationRequest));
        bundleContext.trackResource(ResourceType.MedicationRequest, medicationRequest.getId(), date, title);
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
