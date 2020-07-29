package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestResult;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @PostMapping(Constants.PATH_DATA_TRANSFER)
    public Mono<Void> dataNotification(@RequestBody DataNotificationRequest dataNotificationRequest) {
        return dataFlowService.handleNotification(dataNotificationRequest);
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(Constants.PATH_HEALTH_INFORMATION_HIU_ON_REQUEST)
    public Mono<Void> onInitDataFlowRequest(@Valid @RequestBody DataFlowRequestResult dataFlowRequestResult) {
        return dataFlowService.updateDataFlowRequest(dataFlowRequestResult);
    }
}
