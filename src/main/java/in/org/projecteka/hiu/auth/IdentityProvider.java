package in.org.projecteka.hiu.auth;

import reactor.core.publisher.Mono;

public interface IdentityProvider {
    Mono<String> fetchCertificate();
}
