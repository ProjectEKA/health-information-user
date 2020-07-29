package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import in.org.projecteka.hiu.dataflow.model.HealthInformationFetchRequest;
import in.org.projecteka.hiu.dataflow.model.PatientHealthInformation;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatusResponse;
import in.org.projecteka.hiu.dataflow.model.DataRequestStatusCheckRequest;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static in.org.projecteka.hiu.common.Constants.API_PATH_FETCH_PATIENT_HEALTH_INFO;
import static in.org.projecteka.hiu.common.Constants.API_PATH_GET_INFO_FOR_SINGLE_CONSENT_REQUEST;
import static in.org.projecteka.hiu.common.Constants.API_PATH_GET_ATTACHMENT;
import static in.org.projecteka.hiu.common.Constants.CM_API_PATH_GET_ATTACHMENT;
import static in.org.projecteka.hiu.common.Constants.API_PATH_GET_HEALTH_INFO_STATUS;

@SuppressWarnings("MVCPathVariableInspection")
@RestController
@AllArgsConstructor
public class HealthInfoController {
    private final HealthInfoManager healthInfoManager;
    private final DataFlowServiceProperties serviceProperties;

    @GetMapping(API_PATH_GET_INFO_FOR_SINGLE_CONSENT_REQUEST)
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

    @PostMapping(API_PATH_FETCH_PATIENT_HEALTH_INFO)
    public Mono<PatientHealthInformation> fetchHealthInformation(@RequestBody HealthInformationFetchRequest dataRequest) {
        var limit = Math.min(dataRequest.getLimit(serviceProperties.getDefaultPageSize()), serviceProperties.getMaxPageSize());
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUsername)
                .flatMap(username -> healthInfoManager.fetchHealthInformation(
                        dataRequest.getRequestIds(), username, limit, dataRequest.getOffset()))
                .map(tuple -> PatientHealthInformation.builder()
                        .size(tuple.getT2())
                        .limit(limit)
                        .offset(dataRequest.getOffset())
                        .entries(tuple.getT1()).build());
    }

    @GetMapping(value = {API_PATH_GET_ATTACHMENT, CM_API_PATH_GET_ATTACHMENT})
    public Mono<ResponseEntity<FileSystemResource>> fetchHealthInformation(
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @PathVariable(value = "file-name") String fileName) {
        String transactionId = healthInfoManager.getTransactionIdForConsentRequest(consentRequestId);
        Path filePath = Paths.get(serviceProperties.getLocalStoragePath(), TokenUtils.encode(consentRequestId), TokenUtils.encode(transactionId), fileName);
        String contentDispositionHeaderValue = String.format("attachment; %s", filePath.getFileName().toString());
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionHeaderValue)
                .contentType(responseContentType(filePath))
                .body(new FileSystemResource(filePath)));
    }

    @PostMapping(API_PATH_GET_HEALTH_INFO_STATUS)
    public Mono<DataRequestStatusResponse> fetchHealthInformationStatus(@RequestBody DataRequestStatusCheckRequest dataRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (Caller) securityContext.getAuthentication().getPrincipal())
                .map(Caller::getUsername)
                .flatMapMany(username -> healthInfoManager.fetchHealthInformationStatus(dataRequest.getRequestIds()))
                .collectList()
                .map(DataRequestStatusResponse::new);
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
