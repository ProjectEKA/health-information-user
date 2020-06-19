package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResult;
import in.org.projecteka.hiu.ServiceCaller;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

@RestController
@AllArgsConstructor
public class DataFlowController {
    private final DataFlowService dataFlowService;

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/data/notification")
    public Mono<Void> dataNotification(@RequestBody DataNotificationRequest dataNotificationRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> (ServiceCaller) securityContext.getAuthentication().getPrincipal())
                .map(ServiceCaller::getClientId)
                .flatMap(requesterId -> dataFlowService.handleNotification(dataNotificationRequest, requesterId));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/v1/health-information/hiu/on-request")
    public Mono<Void> onInitDataFlowRequest(@Valid @RequestBody DataFlowRequestResult dataFlowRequestResult) {
        return dataFlowService.updateDataFlowRequest(dataFlowRequestResult);
    }
}
