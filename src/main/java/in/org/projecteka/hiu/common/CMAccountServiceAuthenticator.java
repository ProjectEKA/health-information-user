package in.org.projecteka.hiu.common;

import com.nimbusds.jose.JWSObject;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.user.SessionServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;

import static java.lang.String.format;

public class CMAccountServiceAuthenticator  implements Authenticator{
    private final SessionServiceClient sessionServiceClient;
    private final Logger logger = LoggerFactory.getLogger(CMAccountServiceAuthenticator.class);

    public CMAccountServiceAuthenticator(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public Mono<Caller> verify(String token) {
        try {
            var parts = token.split(" ");
            if (parts.length != 2)
                return Mono.empty();

            var jwsObject = JWSObject.parse(parts[1]);

            var jsonObject = jwsObject.getPayload().toJSONObject();
            return sessionServiceClient.validateToken(token)
                    .flatMap(isValid -> {
                        if (isValid) {
                            return Mono.just(Caller.builder().username(jsonObject.getAsString("sub"))
                                    .isServiceAccount(false).build());
                        }
                        return Mono.empty();
                    }).onErrorResume(error -> Mono.empty());
        } catch (ParseException e) {
            logger.error(format("Unauthorized access with token: %s %s", token, e));
        }
        return Mono.empty();
    }
}
