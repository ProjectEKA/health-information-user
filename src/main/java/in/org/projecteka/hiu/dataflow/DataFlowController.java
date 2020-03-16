package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.TokenUtils;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class DataFlowController {
    private DataFlowService dataFlowService;

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/data/notification")
    public Mono<Void> dataNotification(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestBody DataNotificationRequest dataNotificationRequest) {
        String requesterId = TokenUtils.decode(authorization);
        return dataFlowService.handleNotification(dataNotificationRequest, requesterId);
    }
}
