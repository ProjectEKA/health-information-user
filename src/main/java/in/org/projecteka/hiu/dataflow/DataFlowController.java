package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.HealthInformation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class DataFlowController {
    private DataFlowService dataFlowService;
    private DataFlowServiceProperties dataFlowServiceProperties;

    @PostMapping("/data/notification")
    public Mono<Void> dataNotification(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestBody DataNotificationRequest dataNotificationRequest) {
        return dataFlowService.handleNotification(dataNotificationRequest);
    }

    @GetMapping("/health-information/fetch/{consent-request-id}")
    public Mono<HealthInformation> fetchHealthInformation(
            @PathVariable(value = "consent-request-id") String consentRequestId,
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(defaultValue = "-1") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int pageSize = getPageSize(limit);
        String requesterId = TokenUtils.decode(authorization);
        return dataFlowService.fetchHealthInformation(consentRequestId, requesterId).collectList()
                .flatMap(dataEntries -> Mono.just(HealthInformation.builder()
                        .size(dataEntries.size())
                        .limit(pageSize)
                        .offset(offset)
                        .entries(dataEntries).build()));
    }

    private int getPageSize(int limit) {
        if (limit < 0) {
            return dataFlowServiceProperties.getDefaultPageSize();
        }
        return Math.min(limit, dataFlowServiceProperties.getMaxPageSize());
    }
}
