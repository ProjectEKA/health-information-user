package in.org.projecteka.hiu.user;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class SessionController {
    private final SessionService sessionService;

    @PostMapping("/sessions")
    public Mono<Session> forNew(@RequestBody SessionRequest sessionRequest) {
        return sessionService.forNew(sessionRequest);
    }
}
