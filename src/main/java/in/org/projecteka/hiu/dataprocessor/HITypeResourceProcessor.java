package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;


public interface HITypeResourceProcessor {
    boolean supports(ResourceType type);

    void process(Resource resource, DataContext dataContext, BundleContext bundleContext);
}
