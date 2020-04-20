package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@AllArgsConstructor
public class HealthInfoController {
    private final HealthInfoManager healthInfoManager;
    private final DataFlowServiceProperties serviceProperties;

    @GetMapping("/health-information/fetch/{consent-request-id}")
    public Mono<HealthInformation> fetchHealthInformation(
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @RequestParam(defaultValue = "${hiu.dataflowservice.defaultPageSize}") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUsername)
                .flatMapMany(username -> healthInfoManager.fetchHealthInformation(consentRequestId, username))
                .collectList()
                .map(dataEntries -> HealthInformation.builder()
                        .size(dataEntries.size())
                        .limit(Math.min(limit, serviceProperties.getMaxPageSize()))
                        .offset(offset)
                        .entries(dataEntries).build());
    }

    @GetMapping("/health-information/fetch/{consent-request-id}/attachments/{file-name}")
    public Mono<ResponseEntity<FileSystemResource>> fetchHealthInformation(
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @PathVariable(value = "file-name") String fileName) {
        Path filePath = Paths.get(serviceProperties.getLocalStoragePath(), TokenUtils.encode(consentRequestId), fileName);
        String contentDispositionHeaderValue = String.format("attachment; %s", filePath.getFileName().toString());
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionHeaderValue)
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
