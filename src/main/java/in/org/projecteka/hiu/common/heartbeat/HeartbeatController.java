package in.org.projecteka.hiu.common.heartbeat;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class HeartbeatController {
    private Heartbeat heartbeat;

    @GetMapping(Constants.PATH_HEARTBEAT)
    public Mono<HeartbeatResponse> getProvidersByName() {
        return heartbeat.getStatus();
    }
}
