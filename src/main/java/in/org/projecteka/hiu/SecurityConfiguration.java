package in.org.projecteka.hiu;

import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
import in.org.projecteka.hiu.user.Role;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static in.org.projecteka.hiu.common.Constants.APP_PATH_PATIENT_CONSENT_REQUEST;
import static in.org.projecteka.hiu.common.Constants.PATH_CONSENTS_HIU_NOTIFY;
import static in.org.projecteka.hiu.common.Constants.PATH_CONSENTS_ON_FETCH;
import static in.org.projecteka.hiu.common.Constants.PATH_CONSENTS_ON_FIND;
import static in.org.projecteka.hiu.common.Constants.PATH_CONSENT_REQUESTS_ON_INIT;
import static in.org.projecteka.hiu.common.Constants.PATH_DATA_TRANSFER;
import static in.org.projecteka.hiu.common.Constants.PATH_HEALTH_INFORMATION_HIU_ON_REQUEST;
import static in.org.projecteka.hiu.common.Constants.PATH_HEARTBEAT;
import static in.org.projecteka.hiu.user.Role.GATEWAY;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    protected static final String[] GATEWAY_APIS = new String[]{
            PATH_CONSENT_REQUESTS_ON_INIT,
            PATH_CONSENTS_HIU_NOTIFY,
            PATH_CONSENTS_ON_FETCH,
            PATH_CONSENTS_ON_FIND,
            PATH_HEALTH_INFORMATION_HIU_ON_REQUEST
    };

    private static final List<Map.Entry<HttpMethod, String>> CM_PATIENT_APIS = List.of(
            Map.entry(HttpMethod.GET, "/cm/hello"),
            Map.entry(HttpMethod.POST, APP_PATH_PATIENT_CONSENT_REQUEST));

    private static final List<Map.Entry<HttpMethod, String>> DOCTOR_AND_PATIENT_COMMON_APIS = List.of(
            Map.entry(HttpMethod.GET, "/health-information/fetch/{consent-request-id}/attachments/{file-name}")
    );

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity httpSecurity,
            ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContextRepository) {
        final String[] allowedLists = {"/**.json",
                "/ValueSet/**.json",
                "/**.html",
                "/**.js",
                "/**.yaml",
                "/**.css",
                "/**.png",
                "/health-information/fetch/**/attachments/**",
                PATH_DATA_TRANSFER,
                PATH_HEARTBEAT,
                "/sessions",
                "/config"};

        httpSecurity.authorizeExchange().pathMatchers(allowedLists).permitAll();
        httpSecurity.httpBasic().disable().formLogin().disable().csrf().disable().logout().disable();
        httpSecurity.authorizeExchange().pathMatchers(HttpMethod.POST, "/users").hasAnyRole(Role.ADMIN.toString());
        httpSecurity.authorizeExchange().pathMatchers(HttpMethod.PUT, "/users/password").authenticated();
        CM_PATIENT_APIS.forEach(entry -> httpSecurity.authorizeExchange().pathMatchers(entry.getValue()).authenticated());
        DOCTOR_AND_PATIENT_COMMON_APIS.forEach(entry -> httpSecurity.authorizeExchange().pathMatchers(entry.getValue()).authenticated());
        httpSecurity.authorizeExchange()
                .pathMatchers(GATEWAY_APIS)
                .hasAnyRole(GATEWAY.toString())
                .pathMatchers("/**")
                .hasAnyRole("VERIFIED");
        return httpSecurity
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager() {
        return new AuthenticationManager();
    }

    @Bean
    public SecurityContextRepository contextRepository(GatewayTokenVerifier gatewayTokenVerifier,
                                                       @Qualifier("hiuUserAuthenticator") Authenticator authenticator,
                                                       @Qualifier("userAuthenticator") Authenticator userAuthenticator) {
        return new SecurityContextRepository(gatewayTokenVerifier, authenticator, userAuthenticator);
    }

    @AllArgsConstructor
    private static class SecurityContextRepository implements ServerSecurityContextRepository {
        private final GatewayTokenVerifier gatewayTokenVerifier;
        private final Authenticator authenticator;
        private final Authenticator userAuthenticator;

        @Override
        public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
            throw new UnsupportedOperationException("No need right now!");
        }

        @Override
        public Mono<SecurityContext> load(ServerWebExchange exchange) {
            var token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            var requestPath = exchange.getRequest().getPath().toString();
            var requestMethod = exchange.getRequest().getMethod();

            if (isEmpty(token)) {
                return Mono.empty();
            }
            if (isGatewayOnlyRequest(requestPath)) {
                return checkGateway(token);
            }

            if(isDoctorOrPatientRequest(requestPath, requestMethod)){
                return checkUserToken(token).switchIfEmpty(check(token));
            }

            if (isCMPatientRequest(requestPath, requestMethod)) {
                return checkUserToken(token);
            }

            return check(token);
        }

        private Mono<SecurityContext> checkUserToken(String token) {
            return userAuthenticator.verify(token)
                    .map(caller -> new UsernamePasswordAuthenticationToken(caller, token, new ArrayList<>()))
                    .map(SecurityContextImpl::new);
        }

        private Mono<SecurityContext> check(String token) {
            return authenticator.verify(token)
                    .map(caller ->
                    {
                        var grantedAuthority = new ArrayList<SimpleGrantedAuthority>();
                        if (caller.isVerified()) {
                            grantedAuthority.add(new SimpleGrantedAuthority("ROLE_VERIFIED"));
                        }
                        caller.getRole().ifPresent(role ->
                                grantedAuthority.add(new SimpleGrantedAuthority("ROLE_".concat(role))));
                        return new UsernamePasswordAuthenticationToken(caller, token, grantedAuthority);
                    })
                    .map(SecurityContextImpl::new);
        }

        private Mono<SecurityContext> checkGateway(String token) {
            return gatewayTokenVerifier.verify(token)
                    .map(serviceCaller -> {
                        var authorities = serviceCaller.getRoles()
                                .stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name().toUpperCase()))
                                .collect(toList());
                        return new UsernamePasswordAuthenticationToken(serviceCaller, token, authorities);
                    })
                    .map(SecurityContextImpl::new);
        }

        private boolean isGatewayOnlyRequest(String url) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return List.of(GATEWAY_APIS)
                    .stream()
                    .anyMatch(pattern -> antPathMatcher.matchStart(pattern, url));
        }

        private boolean isCMPatientRequest(String path, HttpMethod method) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return CM_PATIENT_APIS.stream()
                    .anyMatch(pattern ->
                            antPathMatcher.matchStart(pattern.getValue(), path) && method == pattern.getKey());
        }

        private boolean isDoctorOrPatientRequest(String path, HttpMethod method) {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return DOCTOR_AND_PATIENT_COMMON_APIS.stream()
                    .anyMatch(pattern ->
                            antPathMatcher.matchStart(pattern.getValue(), path) && method == pattern.getKey());
        }
    }

    private static class AuthenticationManager implements ReactiveAuthenticationManager {
        @Override
        public Mono<Authentication> authenticate(Authentication authentication) {
            var token = authentication.getCredentials().toString();
            var auth = new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(),
                    token,
                    new ArrayList<SimpleGrantedAuthority>());
            return Mono.just(auth);
        }
    }
}
