package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import in.org.projecteka.hiu.dicomweb.DicomStudy;
import in.org.projecteka.hiu.dicomweb.OrthancDicomWebServer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Media;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;

public class DiagnosticReportResourceProcessor implements HITypeResourceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticReportResourceProcessor.class);
    public static final String VS_SYSTEM_DIAGNOSTIC_SERVICE_SECTIONS = "http://hl7.org/fhir/ValueSet/diagnostic" +
            "-service-sections";
    public static final String RADIOLOGY_CATEGORY_CODE = "RAD";

    private final OrthancDicomWebServer localDicomWebServer;

    public DiagnosticReportResourceProcessor(OrthancDicomWebServer localDicomWebServer) {
        this.localDicomWebServer = localDicomWebServer;
    }

    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.DiagnosticReport);
    }

    /**
     * Usually invoked by HealthDataProcessor, but in cases like Composition Resource processing, CompositionResourceProcessor
     * can invoke this as well, if one of its entries is DiagnosticReport.
     * If it is being processed from a root resource, and typically there is no need to track the resource date.
     * for example, if the DiagnosticReport is part of the Discharge Summary (Composition), then the DiagnosticReport
     * will need to be displayed in the context of Discharge Summary and hence no need to track it separately.
     *
     * The complication is - what if a DiagnosticReport or other independent root resources are also included
     * in the root resource? Or a MedicationRequest done in IPD (for which a prescription) was given to patient,
     * is also referenced in the Discharge Summary. Then the prescription does not appear independently, which is not correct.
     *
     * NOTE: For now, we are tracking DiagnosticReports by date as well.
     * @param resource - the DiagnosticReport resource
     * @param dataContext - overall transferred health data  context
     * @param bundleContext - context of the current bundle in process
     * @param processContext - from which root resource process context is this being called.
     */
    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
            return;
        }
        DiagnosticReport diagnosticReport = (DiagnosticReport) resource;
        processPresentedForm(diagnosticReport, dataContext.getLocalStoragePath());
        processMedia(diagnosticReport, dataContext.getLocalStoragePath(), bundleContext);
        processResults(diagnosticReport, dataContext, bundleContext, processContext);
        bundleContext.doneProcessing(diagnosticReport);
        Date reportDate = getReportDate(diagnosticReport, bundleContext, processContext);
        String title = String.format("Diagnostic Report : %s", FHIRUtils.getDisplay(diagnosticReport.getCode()));
        bundleContext.trackResource(ResourceType.DiagnosticReport, diagnosticReport.getId(), reportDate, title);
    }

    private ResourceType getDiagnosticReportResourceType() {
        return ResourceType.DiagnosticReport;
    }

    private void processResults(DiagnosticReport diagnosticReport, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (diagnosticReport.hasResult()) {
            ProcessContext reportProcessCtx = processContext != null ? processContext : getReportContext(diagnosticReport, bundleContext);
            diagnosticReport.getResult().stream().forEach(ref -> {
                IBaseResource resource = ref.getResource();
                if (resource == null) {
                    logger.warn(String.format("Diagnostic results not found. diagnosticReport id: %s, result reference: %s",
                            diagnosticReport.getId(), ref.getReference()));
                }
                if (!(resource instanceof Resource)) {
                    return;
                }
                Resource bundleResource = (Resource) resource;
                HITypeResourceProcessor resProcessor = bundleContext.findResourceProcessor(bundleResource.getResourceType());
                if (resProcessor != null) {
                    resProcessor.process(bundleResource, dataContext, bundleContext, reportProcessCtx);
                }
            });
        }
    }

    private ProcessContext getReportContext(DiagnosticReport diagnosticReport, BundleContext bundleContext) {
        return new ProcessContext(
               () -> getReportDate(diagnosticReport, bundleContext, null),
               diagnosticReport::getId, this::getDiagnosticReportResourceType);
    }

    private Date getReportDate(DiagnosticReport diagnosticReport, BundleContext bundleContext, ProcessContext processContext) {
        Date date = diagnosticReport.getIssued() != null ? diagnosticReport.getIssued() : diagnosticReport.getEffectiveDateTimeType().getValue();
        if (date == null && processContext != null) {
            date = processContext.getContextDate();
        }
        if (date == null) {
            date = bundleContext.getBundleDate();
        }
        return date;
    }

    private void processMedia(DiagnosticReport diagnosticReport, Path localStoragePath, BundleContext bundleContext) {
        List<DiagnosticReport.DiagnosticReportMediaComponent> mediaList = diagnosticReport.getMedia();
        if (mediaList.isEmpty()) {
            return;
        }
        for (DiagnosticReport.DiagnosticReportMediaComponent media : mediaList) {
            if (!media.hasLink()) {
                continue;
            }
            Media mediaResource = (Media) media.getLink().getResource();
            if (mediaResource == null) {
                continue;
            }
            if (bundleContext.isProcessed(mediaResource)) {
                continue;
            }
            processDiagnosticReportMedia(mediaResource, localStoragePath);
            bundleContext.doneProcessing(mediaResource);
        }
    }

    private void processDiagnosticReportMedia(Media media, Path localStoragePath) {
        Path savedAttachmentPath = new AttachmentDataTypeProcessor().process(media.getContent(), localStoragePath);
        if (isRadiologyFile(media.getContent())) {
            uploadToLocalDicomServer(media.getContent(), savedAttachmentPath);
        }
    }

    private void processPresentedForm(DiagnosticReport diagnosticReport, Path localStorePath) {
        if (diagnosticReport.hasPresentedForm()) {
            List<Attachment> presentedForm = diagnosticReport.getPresentedForm();
            for (Attachment attachment : presentedForm) {
                new AttachmentDataTypeProcessor().process(attachment, localStorePath);
            }
        }
    }

    private boolean isRadiologyFile(Attachment attachment) {
        String extension = AttachmentDataTypeProcessor.getFileExtension(attachment.getContentType().toUpperCase());
        return (extension != null) && extension.equals(".dcm");
    }

    private boolean isRadiologyCategory(DiagnosticReport diagnosticReport) {
        return diagnosticReport.getCategoryFirstRep().getCoding().stream()
                .anyMatch(c -> c.getCode().equalsIgnoreCase(RADIOLOGY_CATEGORY_CODE));
    }

    private void uploadToLocalDicomServer(Attachment content, Path savedFilePath) {
        if (localDicomWebServer.exists()) {
            DicomStudy dicomStudy = localDicomWebServer.upload(savedFilePath);
            content.setUrl(referenceLocalDicomServerUrl(dicomStudy.getStudyInstanceUid()));
        }
    }

    private String referenceLocalDicomServerUrl(String studyInstanceUid) {
        return String.format("/dicom-server/studies/%s", studyInstanceUid);
    }
}
