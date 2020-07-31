package in.org.projecteka.hiu.common;

import com.nimbusds.jose.JWSObject;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import in.org.projecteka.hiu.user.SessionServiceClient;
import in.org.projecteka.hiu.user.TokenValidationRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;

import static java.lang.String.format;

@AllArgsConstructor
public class CMAccountServiceAuthenticator implements Authenticator {
    private final SessionServiceClient sessionServiceClient;
    private static final Logger logger = LoggerFactory.getLogger(CMAccountServiceAuthenticator.class);

    @Override
    public Mono<Caller> verify(String token) {
        try {
            var parts = token.split(" ");
            if (parts.length != 2)
                return Mono.empty();

            var jwsObject = JWSObject.parse(parts[1]);

            var jsonObject = jwsObject.getPayload().toJSONObject();
            return sessionServiceClient.validateToken(TokenValidationRequest.builder().authToken(parts[1]).build())
                    .flatMap(isValid -> {
                        if (Boolean.TRUE.equals(isValid)) {
                            return Mono.just(Caller.builder().username(jsonObject.getAsString("healthId"))
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
