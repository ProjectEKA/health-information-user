package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.ImmunizationRecommendation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.Date;

public class ImmunizationRecommendationProcessor implements HITypeResourceProcessor {
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.ImmunizationRecommendation);
    }

    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
            //if contained within a composition like OPConsultation
            return;
        }
        bundleContext.doneProcessing(resource);

        ImmunizationRecommendation immunizationRecommendation = (ImmunizationRecommendation) resource;
        Date date = immunizationRecommendation.getDate();
        String title = String.format("ImmunizationRecommendation : %s", immunizationRecommendation.getId());
        bundleContext.trackResource(ResourceType.ImmunizationRecommendation, immunizationRecommendation.getId(), date, title);
    }
}
