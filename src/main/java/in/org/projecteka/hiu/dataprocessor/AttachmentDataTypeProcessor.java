package in.org.projecteka.hiu.dataprocessor;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class AttachmentDataTypeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentDataTypeProcessor.class);

    private static final Map<String, String> MEDIA_TO_FILE_EXTENSION = Map.of("APPLICATION/PDF", ".pdf",
            "APPLICATION/DICOM", ".dcm",
            "APPLICATION/MSWORD", ".doc",
            "TEXT/RTF", ".rtf",
            "IMAGE/JPEG", ".jpeg",
            "IMAGE/PNG", ".png",
            "AUDIO/WAV", ".wav",
            "VIDEO/MPEG", ".mpeg");
    public static final String DEFAULT_FILE_EXTENSION = ".txt";

    public static String getFileExtension(String mimeType) {
        return MEDIA_TO_FILE_EXTENSION.get(mimeType);
    }

    public Path process(Attachment attachment, Path localStorePath) {
        if (hasLink(attachment)) {
            return downloadAndSaveFile(attachment, localStorePath);
        } else {
            return saveAttachmentAsFile(attachment, localStorePath);
        }
    }

    private Path saveAttachmentAsFile(Attachment attachment, Path localStorePath) throws RuntimeException {
        if (attachment.getData() != null) {
            byte[] data = Base64.getDecoder().decode(attachment.getDataElement().getValueAsString());
            Path attachmentFilePath = getFileAttachmentPath(attachment, localStorePath);
            try (FileChannel channel = (FileChannel) Files.newByteChannel(attachmentFilePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.allocate(data.length);
                buffer.put(data);
                buffer.flip();
                channel.write(buffer);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
            attachment.setData(null);
            attachment.setUrl(referenceWebUrl(attachmentFilePath));
            return attachmentFilePath;
        } else {
            return downloadAndSaveFile(attachment, localStorePath);
        }
    }

    private Path downloadAndSaveFile(Attachment attachment, Path localStorePath) {
        Path attachmentFilePath = getFileAttachmentPath(attachment, localStorePath);
        HttpGet request = new HttpGet(URI.create(attachment.getUrl()));
        try (CloseableHttpClient client = HttpClientBuilder.create().build();
             CloseableHttpResponse response = client.execute(request)) {
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();
            Files.copy(inputStream, attachmentFilePath);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        attachment.setUrl(referenceWebUrl(attachmentFilePath));
        return attachmentFilePath;
    }

    private Path getFileAttachmentPath(Attachment attachment, Path localStorePath) {
        String randomFileName = UUID.randomUUID().toString() + getFileExtension(attachment);
        return Paths.get(localStorePath.toString(), randomFileName);
    }

    private String getFileExtension(Attachment attachment) {
        String extension = MEDIA_TO_FILE_EXTENSION.get(attachment.getContentType().toUpperCase());
        return (extension != null) ? extension : DEFAULT_FILE_EXTENSION;
    }

    private String referenceWebUrl(Path attachmentFilePath) {
        //TODO create a referenceable path so that, UI can use that. maybe startwith /
        return String.format("/attachments/%s", attachmentFilePath.getFileName().toString());
    }

    private boolean hasLink(Attachment attachment) {
        return (attachment.getUrl() != null) && !attachment.getUrl().isBlank();
    }
}
