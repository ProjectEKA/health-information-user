package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class UserBase64Authenticator implements Authenticator {
    private final Base64Authenticator base64Authenticator;
    private final UserAuthenticator userAuthenticator;

    @Override
    public Mono<Caller> verify(String token) {
        return base64Authenticator.verify(token)
                .switchIfEmpty(userAuthenticator.verify(token));
    }
}
