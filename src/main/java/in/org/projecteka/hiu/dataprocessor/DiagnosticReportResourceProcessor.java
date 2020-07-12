package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dicomweb.DicomStudy;
import in.org.projecteka.hiu.dicomweb.OrthancDicomWebServer;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Media;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext) {
        DiagnosticReport diagnosticReport = (DiagnosticReport) resource;
        processPresentedForm(diagnosticReport, dataContext.getLocalStoragePath());
        processMedia(diagnosticReport, dataContext.getLocalStoragePath(), bundleContext);
    }

    private void processMedia(DiagnosticReport diagnosticReport, Path localStoragePath, BundleContext bundleContext) {
        List<DiagnosticReport.DiagnosticReportMediaComponent> mediaList = diagnosticReport.getMedia();
        if (mediaList.isEmpty()) {
            return;
        }

        boolean radiologyCategory = isRadiologyCategory(diagnosticReport);

        for (DiagnosticReport.DiagnosticReportMediaComponent media : mediaList) {
            if (media.hasLink()) {
                processDiagnosticReportMedia(localStoragePath, radiologyCategory, media);
            }
        }
    }

    private void processDiagnosticReportMedia(Path localStoragePath, boolean radiologyCategory, DiagnosticReport.DiagnosticReportMediaComponent media) {
        Media linkTarget = (Media) media.getLink().getResource();
        Path savedAttachmentPath = new AttachmentDataTypeProcessor().process(linkTarget.getContent(), localStoragePath);
        if (radiologyCategory && isRadiologyFile(linkTarget.getContent())) {
            uploadToLocalDicomServer(linkTarget.getContent(), savedAttachmentPath);
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
