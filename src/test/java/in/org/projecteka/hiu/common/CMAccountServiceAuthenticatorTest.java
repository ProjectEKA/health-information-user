package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.user.SessionServiceClient;
import in.org.projecteka.hiu.user.TokenValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static in.org.projecteka.hiu.common.TestBuilders.string;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class CMAccountServiceAuthenticatorTest {

    @Mock
    SessionServiceClient sessionServiceClient;

    CMAccountServiceAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        initMocks(this);
        authenticator = new CMAccountServiceAuthenticator(sessionServiceClient);
    }

    @Test
    void shouldNotReturnCallerIfTokenIsInvalid() {
        String accessToken = string();
        String testToken = String.format("%s %s", "Bearer", accessToken);

        when(sessionServiceClient.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.just(false));

        StepVerifier.create(authenticator.verify(testToken))
                .verifyComplete();
    }

    @Test
    void shouldReturnCallerIfTokenIsValid() {
        String accessToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJoZWFsdGhJZCI6ImhpbmEucGF0ZWwiLCJqdGkiOiJjMmE0" +
                "NzJmNy0yOWVmLTQxNjktYTQ3YS1mZDkxZDk4MWMyMzkiLCJpYXQiOjE1OTYwODcxODIsImV4cCI6MTU5NjA5MDc4Mn0.IOo3B6" +
                "xATOZMZUFD7grzj4umg09kO5g7wB84rJYswho";
        String testToken = String.format("%s %s", "Bearer", accessToken);
        Caller expectedCaller = Caller.builder()
                .username("hina.patel")
                .isServiceAccount(false)
                .build();

        when(sessionServiceClient.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.just(true));

        StepVerifier.create(authenticator.verify(testToken))
               .expectNext(expectedCaller)
                .verifyComplete();
    }
}