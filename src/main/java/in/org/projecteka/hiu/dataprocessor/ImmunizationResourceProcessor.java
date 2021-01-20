package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class ImmunizationResourceProcessor implements HITypeResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ImmunizationResourceProcessor.class);

    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.Immunization);
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
        Immunization immunization = (Immunization) resource;
        Date date = immunization.getOccurrenceDateTimeType().getValue();
        String title = String.format("Immunization : %s", FHIRUtils.getDisplay(immunization.getVaccineCode()));
        bundleContext.trackResource(ResourceType.Immunization, immunization.getId(), date, title);
    }
}
