package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.consent.TokenUtils;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import static java.lang.String.format;

public class Base64Authenticator implements Authenticator {

    private final Logger logger = Logger.getLogger(Base64Authenticator.class);

    @Override
    public Mono<Caller> verify(String token) {
        try {
            return Mono.justOrEmpty(new Caller(TokenUtils.decode(token), false, null));
        } catch (Exception e) {
            logger.error(format("Unable to decode the token: %s", token));
            return Mono.empty();
        }
    }
}
