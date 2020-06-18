package in.org.projecteka.hiu.dicomweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.LocalDicomServerProperties;
import org.springframework.util.Base64Utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

public class OrthancDicomWebServer {
    LocalDicomServerProperties properties;

    public OrthancDicomWebServer(LocalDicomServerProperties properties) {
        this.properties = properties;
    }

    public boolean exists() {
        String localDicomSrvUrl = properties.getUrl();
        return localDicomSrvUrl != null && !"".equals(localDicomSrvUrl);
    }

    public DicomStudy upload(Path savedFilePath) {
        try {
            DicomInstance dicomInstance = uploadStudyInstance(savedFilePath);
            return retrieveStudyDetails(dicomInstance);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DicomStudy retrieveStudyDetails(DicomInstance dicomInstance) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        if (requiresAuth()) {
            requestBuilder.header("Authorization", authCredentials());
        }
        HttpRequest request = requestBuilder
                .uri(instanceStudyURI(dicomInstance.getStudyUuid()))
                .header("Content-Type", "application/octet-stream")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString());
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper.readValue(response.body(), DicomStudy.class);
    }

    private DicomInstance uploadStudyInstance(Path savedFilePath) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        if (requiresAuth()) {
            requestBuilder.header("Authorization", authCredentials());
        }
        HttpRequest request = requestBuilder
                .uri(studyInstanceURI())
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(savedFilePath))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString());

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper.readValue(response.body(), DicomInstance.class);
    }

    private URI instanceStudyURI(String studyUuid) {
        return URI.create(String.format("%s/studies/%s", properties.getUrl(), studyUuid));
    }

    private URI studyInstanceURI() {
        return URI.create(String.format("%s/instances", properties.getUrl()));
    }

    private boolean requiresAuth() {
        return !"".equals(properties.getUser());
    }

    private String authCredentials() {
        return "Basic " + Base64Utils.encodeToString(String.format("%s:%s", properties.getUser(), properties.getPassword()).getBytes());
    }
}
