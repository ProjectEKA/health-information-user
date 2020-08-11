package in.org.projecteka.hiu.common;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;

import static in.org.projecteka.hiu.common.Constants.BLOCK_LIST;
import static in.org.projecteka.hiu.common.Constants.BLOCK_LIST_FORMAT;
import static java.lang.String.format;

import com.google.common.base.Strings;

import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.just;

@AllArgsConstructor
public class CMAccountServiceOfflineAuthenticator implements Authenticator {
    private final RSASSAVerifier tokenVerifier;
    private final CacheAdapter<String, String> blockListedTokens;
    private static final Logger logger = LoggerFactory.getLogger(CMAccountServiceOfflineAuthenticator.class);

    @Override
    public Mono<Caller> verify(String token) {
        try {
            var parts = token.split(" ");
            if (parts.length != 2) {
                return empty();
            }
            var credentials = parts[1];
            SignedJWT signedJWT = SignedJWT.parse(credentials);
            if (!signedJWT.verify(tokenVerifier)) {
                return empty();
            }
            var healthId = signedJWT.getJWTClaimsSet().getClaim("healthId").toString();
            if (Strings.isNullOrEmpty(healthId)) {
                return empty();
            }
            return blockListedTokens.exists(String.format(BLOCK_LIST_FORMAT, BLOCK_LIST, credentials))
                    .filter(exists -> !exists)
                    .flatMap(uselessFalse -> just(Caller.builder().username(healthId).isServiceAccount(false).build()));
        } catch (ParseException | JOSEException e) {
            logger.error(format("Unauthorized access with token: %s %s", token, e));
            return empty();
        }
    }
}