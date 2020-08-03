package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import reactor.core.publisher.Mono;

public class CMPatientAccountAuthenticator implements Authenticator{
    CMAccountServiceAuthenticator cmAccountServiceAuthenticator;
    CMPatientAuthenticator cmPatientAuthenticator;

    public CMPatientAccountAuthenticator(CMAccountServiceAuthenticator cmAccountServiceAuthenticator,
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
