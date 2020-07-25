package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.ProcessContext;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public class BinaryResourceProcessor implements HITypeResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BinaryResourceProcessor.class);
    @Override
    public boolean supports(ResourceType type) {
        return type.equals(ResourceType.Binary);
    }

    @Override
    public void process(Resource resource, DataContext dataContext, BundleContext bundleContext, ProcessContext processContext) {
        if (bundleContext.isProcessed(resource)) {
            //if contained within a composition like discharge summary or prescription composition
            return;
        }
        Binary binaryResource = (Binary) resource;
        Path localStoragePath = dataContext.getLocalStoragePath();
        byte[] data = Base64.getDecoder().decode(binaryResource.getContentAsBase64());
        String randomFileName = UUID.randomUUID().toString() + getFileExtension(binaryResource.getContentType());
        Path localPath = Paths.get(localStoragePath.toString(), randomFileName);
        try (FileChannel channel = (FileChannel) Files.newByteChannel(localPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(data.length);
            buffer.put(data);
            buffer.flip();
            channel.write(buffer);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
        binaryResource.setData(String.format("/attachments/%s", localPath.getFileName().toString()).getBytes());
        bundleContext.doneProcessing(binaryResource);
        Date contextDate = processContext != null ? processContext.getContextDate() : null;
        if (contextDate != null) {
            bundleContext.trackResource(ResourceType.Binary, binaryResource.getId(), contextDate, "Binary");
        }
    }

    private String getFileExtension(String contentType) {
        String extension = AttachmentDataTypeProcessor.getFileExtension(contentType.toUpperCase());
        return (extension != null) ? extension : AttachmentDataTypeProcessor.DEFAULT_FILE_EXTENSION;
    }
}
