package in.org.projecteka.hiu.common;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import in.org.projecteka.hiu.Caller;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import java.text.ParseException;

import static java.lang.String.format;

public class UserAuthenticator implements Authenticator {

    private final MACVerifier verifier;
    private final Logger logger = Logger.getLogger(UserAuthenticator.class);

    public UserAuthenticator(byte[] sharedSecret) throws JOSEException {
        verifier = new MACVerifier(sharedSecret);
    }

    @Override
    public Mono<Caller> verify(String token) {
        try {
            var parts = token.split(" ");
            if (parts.length != 2)
                return Mono.empty();

            var jwsObject = JWSObject.parse(parts[1]);
            if (!isValidToken(jwsObject)) {
                logger.error(format("Unauthorized access with token: %s", token));
                return Mono.empty();
            }

            var jsonObject = jwsObject.getPayload().toJSONObject();
            var isVerified = Boolean.parseBoolean(jsonObject.getAsString("isVerified"));
            return Mono.just(new Caller(jsonObject.getAsString("username"), false, jsonObject.getAsString("role"), isVerified));
        } catch (ParseException | JOSEException e) {
            logger.error(format("Unauthorized access with token: %s %s", token, e));
        }
        return Mono.empty();
    }

    private boolean isValidToken(JWSObject jwsObject) throws JOSEException {
        return jwsObject.verify(verifier);
    }
}
