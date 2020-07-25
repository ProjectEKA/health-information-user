package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

public class ConditionResourceProcessor implements HITypeResourceProcessor {
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.Condition);
    }

    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
            //if contained within a composition like discharge summary
            return;
        }
        bundleContext.doneProcessing(resource);
        if (processContext != null && processContext.getContextResourceType().equals(ResourceType.MedicationRequest)) {
            return;
        }
        if (processContext != null) {
            //if processed as part of composition or other parent resource context, we do not need to track individual medicationRequest
            return;
        }
        Condition condition = (Condition) resource;
        String title = String.format("Condition : %s", FHIRUtils.getDisplay(condition.getCode()));
        bundleContext.trackResource(ResourceType.Condition, condition.getId(), condition.getRecordedDate(), title);

    }
}
