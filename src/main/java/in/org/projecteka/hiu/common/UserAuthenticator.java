package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class UserAuthenticator implements Authenticator {
    private final JwtAuthenticator jwtAuthenticator;

    @Override
    public Mono<Caller> verify(String token) {
        return jwtAuthenticator.verify(token);
    }
}
