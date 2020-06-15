package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.GatewayCaller;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class DataFlowController {
    private final DataFlowService dataFlowService;

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/data/notification")
    public Mono<Void> dataNotification(@RequestBody DataNotificationRequest dataNotificationRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (GatewayCaller) securityContext.getAuthentication().getPrincipal())
                .map(GatewayCaller::getUsername)
                .flatMap(requesterId -> dataFlowService.handleNotification(dataNotificationRequest, requesterId));
    }
}
