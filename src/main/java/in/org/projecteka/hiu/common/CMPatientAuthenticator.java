package in.org.projecteka.hiu.common;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.common.cache.CacheAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;

import static in.org.projecteka.hiu.common.Constants.BLOCK_LIST;
import static in.org.projecteka.hiu.common.Constants.BLOCK_LIST_FORMAT;

public class CMPatientAuthenticator implements Authenticator {
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final CacheAdapter<String, String> blockListedTokens;
    private final Logger logger = LoggerFactory.getLogger(CMPatientAuthenticator.class);


    public CMPatientAuthenticator(JWKSet jwkSet, ConfigurableJWTProcessor<SecurityContext> jwtProcessor,
                                  CacheAdapter<String, String> blockListedTokens) {
        var immutableJWKSet = new ImmutableJWKSet<>(jwkSet);
        this.blockListedTokens = blockListedTokens;
        this.jwtProcessor = jwtProcessor;
        this.jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));
        JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
        JWSKeySelector<SecurityContext> keySelector;
        keySelector = new JWSVerificationKeySelector<>(expectedJWSAlg, immutableJWKSet);
        this.jwtProcessor.setJWSKeySelector(keySelector);
        this.jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().build(),
                new HashSet<>(Arrays.asList("sub", "iat", "exp", "scope", "preferred_username"))));
    }

    @Override
    public Mono<Caller> verify(String token) {
        logger.debug("Authenticating {}", token);
        var parts = token.split(" ");
        if (parts.length != 2) {
            return Mono.empty();
        }
        var credentials = parts[1];
        return blockListedTokens.exists(String.format(BLOCK_LIST_FORMAT, BLOCK_LIST, credentials))
                .filter(exists -> !exists)
                .flatMap(uselessFalse -> Mono.create(monoSink -> {
                    JWTClaimsSet jwtClaimsSet;
                    try {
                        jwtClaimsSet = jwtProcessor.process(credentials, null);
                    } catch (ParseException | BadJOSEException | JOSEException e) {
                        logger.error("Unauthorized access", e);
                        monoSink.success();
                        return;
                    }
                    try {
                        monoSink.success(from(jwtClaimsSet.getStringClaim("preferred_username")));
                    } catch (ParseException e) {
                        logger.error(e.getMessage());
                        monoSink.success();
                    }
                }));
    }

    protected Caller from(String preferredUsername) {
        final String serviceAccountPrefix = "service-account-";
        var serviceAccount = preferredUsername.startsWith(serviceAccountPrefix);
        var userName = serviceAccount ? preferredUsername.substring(serviceAccountPrefix.length()) : preferredUsername;
        return new Caller(userName, serviceAccount, null, false);
    }
}
