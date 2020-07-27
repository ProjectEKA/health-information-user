package in.org.projecteka.hiu.common.heartbeat;

import in.org.projecteka.hiu.common.Constants;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.common.heartbeat.model.Status.UP;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@AllArgsConstructor
public class HeartbeatController {
    private final Heartbeat heartbeat;

    @GetMapping(Constants.PATH_HEARTBEAT)
    public Mono<ResponseEntity<HeartbeatResponse>> getProvidersByName() {
        return heartbeat.getStatus()
                .map(heartbeatResponse ->
                        heartbeatResponse.getStatus() == UP
                        ? new ResponseEntity<>(heartbeatResponse, OK)
                        : new ResponseEntity<>(heartbeatResponse, SERVICE_UNAVAILABLE));
    }
}
