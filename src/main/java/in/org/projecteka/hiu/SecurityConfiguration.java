package in.org.projecteka.hiu;

import in.org.projecteka.hiu.common.Authenticator;
import in.org.projecteka.hiu.common.GatewayTokenVerifier;
import in.org.projecteka.hiu.user.Role;
import lombok.AllArgsConstructor;
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

import static in.org.projecteka.hiu.common.Constants.V_1_CONSENTS_HIU_NOTIFY;
import static in.org.projecteka.hiu.common.Constants.V_1_CONSENTS_ON_FETCH;
import static in.org.projecteka.hiu.common.Constants.V_1_CONSENTS_ON_FIND;
import static in.org.projecteka.hiu.common.Constants.V_1_CONSENT_REQUESTS_ON_INIT;
import static in.org.projecteka.hiu.common.Constants.V_1_HEALTH_INFORMATION_HIU_ON_REQUEST;
import static in.org.projecteka.hiu.user.Role.GATEWAY;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    protected static final String[] GATEWAY_APIS = new String[]{
            V_1_CONSENT_REQUESTS_ON_INIT,
            V_1_CONSENTS_HIU_NOTIFY,
            V_1_CONSENTS_ON_FETCH,
            V_1_CONSENTS_ON_FIND,
            V_1_HEALTH_INFORMATION_HIU_ON_REQUEST
    };

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
                                       "/data/notification",
                                       "/sessions",
                                       "/config"};
        httpSecurity.authorizeExchange().pathMatchers(allowedLists).permitAll();
        httpSecurity.httpBasic().disable().formLogin().disable().csrf().disable().logout().disable();
        httpSecurity.authorizeExchange().pathMatchers(HttpMethod.POST, "/users").hasAnyRole(Role.ADMIN.toString());
        httpSecurity.authorizeExchange().pathMatchers(HttpMethod.PUT, "/users/password").authenticated();
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
                                                       Authenticator authenticator) {
        return new SecurityContextRepository(gatewayTokenVerifier, authenticator);
    }

    @AllArgsConstructor
    private static class SecurityContextRepository implements ServerSecurityContextRepository {
        private final GatewayTokenVerifier gatewayTokenVerifier;
        private final Authenticator authenticator;

        @Override
        public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
            throw new UnsupportedOperationException("No need right now!");
        }

        @Override
        public Mono<SecurityContext> load(ServerWebExchange exchange) {
            var token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (isEmpty(token)) {
                return Mono.empty();
            }
            if (isGatewayOnlyRequest(exchange.getRequest().getPath().toString())) {
                return checkGateway(token);
            }
            return check(token);
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
