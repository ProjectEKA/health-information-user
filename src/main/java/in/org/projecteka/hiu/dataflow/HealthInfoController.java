package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@AllArgsConstructor
public class HealthInfoController {
    private HealthInfoManager healthInfoManager;
    private DataFlowServiceProperties serviceProperties;

    @GetMapping("/health-information/fetch/{consent-request-id}")
    public Mono<HealthInformation> fetchHealthInformation(
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(defaultValue = "${hiu.dataflowservice.defaultPageSize}") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String requesterId = TokenUtils.decode(authorization);
        return healthInfoManager.fetchHealthInformation(consentRequestId, requesterId).collectList()
                .map(dataEntries -> {
                    HealthInformation hi = HealthInformation.builder()
                            .size(dataEntries.size())
                            .limit(Math.min(limit, serviceProperties.getMaxPageSize()))
                            .offset(offset)
                            .entries(dataEntries).build();
                    return hi;
                });
    }


    @GetMapping("/consent-requests/{consent-request-id}/attachments/{file-name}")
    public Mono<ResponseEntity<FileSystemResource>> fetchHealthInformation(
            ServerHttpResponse response,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @PathVariable(value = "file-name") String fileName) {
        //String requesterId = TokenUtils.decode(authorization);
        Path filePath = Paths.get(serviceProperties.getLocalStoragePath(), TokenUtils.encode(consentRequestId), fileName);
        String contentDispositionHeaderValue = String.format("attachment; %s",filePath.getFileName().toString());
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,contentDispositionHeaderValue)
                .contentType(responseContentType(filePath))
                .body(new FileSystemResource(filePath)));
    }

    @SneakyThrows
    private MediaType responseContentType(Path filePath) {
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }

}
