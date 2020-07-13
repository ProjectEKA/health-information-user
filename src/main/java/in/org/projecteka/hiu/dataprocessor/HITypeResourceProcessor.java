package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;


public interface HITypeResourceProcessor {
    boolean supports(ResourceType type);

    /**
     * Usually invoked by HealthDataProcessor or another root HITypeResourceProcessor.
     * Typically the HIU will process resources that need further processing - like downloading an attachment or media
     * or for the purpose of tracking resource dates.
     * If the resource is being processed from a root resource, typically there is no need to track individual resources by date.
     * for example, if the DiagnosticReport has results, then the result observations need not be individually tracked.
     * Such references will probably be displayed in the context of root resource.
     *
     * The complication however is - what if a DiagnosticReport or other independent root resources are also included
     * in the root resource? Or a MedicationRequest done in IPD (for which a prescription) was given to patient,
     * is also referenced in the Discharge Summary. Then the prescription does not appear independently, which is not correct.
     * Hence the decision is on the individual HITypeResourceProcessor.

     * @param resource - resource that needs to be processed
     * @param dataContext - transferred health data  context
     * @param bundleContext - context of the current bundle in process
     * @param processContext - from which root resource process context is this being called
     */
    void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext);
}
