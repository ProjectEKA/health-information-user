package in.org.projecteka.hiu.common;

import reactor.core.publisher.Mono;
import in.org.projecteka.hiu.Caller;

public class CMHealthAccountTokenAuthenticator implements Authenticator {
    CMAccountServiceAuthenticator cmAccountServiceAuthenticator;
    CMPatientAuthenticator cmPatientAuthenticator;

    public CMHealthAccountTokenAuthenticator(CMAccountServiceAuthenticator cmAccountServiceAuthenticator,
                                             CMPatientAuthenticator cmPatientAuthenticator) {
        this.cmAccountServiceAuthenticator = cmAccountServiceAuthenticator;
        this.cmPatientAuthenticator = cmPatientAuthenticator;

    }
    @Override
    public Mono<Caller> verify(String token) {
        return cmAccountServiceAuthenticator.verify(token)
                .switchIfEmpty(Mono.defer(()-> cmPatientAuthenticator.verify(token)));
    }
}
