package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.TokenUtils;
import reactor.core.publisher.Mono;

public class Authenticator {
    public Mono<Caller> verify(String token) {
        return Mono.justOrEmpty(new Caller(TokenUtils.decode(token), false, null));
    }
}
