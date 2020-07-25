package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.Date;

public class ObservationResourceProcessor implements HITypeResourceProcessor {
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.Observation);
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
        Observation obs = (Observation) resource;
        String title = String.format("Observation : %s", FHIRUtils.getDisplay(obs.getCode()));
        bundleContext.trackResource(ResourceType.Observation, obs.getId(), getObsDate(obs), title);
    }

    private Date getObsDate(Observation obs) {
        if (obs.hasIssued()) {
            return obs.getIssued();
        }
        if (obs.hasEffectiveDateTimeType()) {
            return obs.getEffectiveDateTimeType().getValueAsCalendar().getTime();
        }
        return null;
    }
}
