package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import reactor.core.publisher.Mono;

public interface Authenticator {
    Mono<Caller> verify(String token);
}
