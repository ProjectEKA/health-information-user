package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import reactor.core.publisher.Mono;

public class CMPatientAccountServiceAuthenticator implements Authenticator{
    CMPatientAuthenticator cmPatientAuthenticator;
    CMAccountServiceAuthenticator cmAccountServiceAuthenticator;

    public CMPatientAccountServiceAuthenticator(CMPatientAuthenticator cmPatientAuthenticator,
                                                CMAccountServiceAuthenticator cmAccountServiceAuthenticator) {
        this.cmPatientAuthenticator = cmPatientAuthenticator;
        this.cmAccountServiceAuthenticator = cmAccountServiceAuthenticator;
    }
    @Override
    public Mono<Caller> verify(String token) {
        return cmAccountServiceAuthenticator.verify(token)
                .switchIfEmpty(Mono.defer(()-> cmPatientAuthenticator.verify(token)))
                .flatMap(Mono::just);
    }
}
