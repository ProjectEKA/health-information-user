package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.Caller;
import in.org.projecteka.hiu.ConsentManagerServiceProperties;
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

    @Mock
    ConsentManagerServiceProperties consentManagerServiceProperties;

    CMAccountServiceAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        initMocks(this);
        authenticator = new CMAccountServiceAuthenticator(sessionServiceClient, consentManagerServiceProperties);
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
        String accessToken = "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiI0MTc2LTU3MDctMTExMC01OSIsIlVzZXJOYW1lIjoiaGluYS5wYXRl" +
                "bCIsImV4cCI6MTU5NTk2MDI5OSwiTW9iaWxlIjoiODM3NTkzOTAwNiIsImlhdCI6MTU5NTk1MzA5OX0.pF5aDbKr2WnFDnlJ0H" +
                "7KbT_nupoMw3H_TEKIhP8-f2rWhurwpw6bYQjLYHU1E8nVeMXko3LpcBCYTHy8nd6goginkfwQ2J8FQl5pNAvDefaZ9mexhUE0" +
                "Dgou1mstbLQLFxPZHIp7t5wnI-gFP7TGNFUyjlWuhpKlHRB2qDbtjtdMk0FUdAkVjFpzhOz6YwGH5HNxHTSb4E2XzaYbf8wYxe" +
                "DxDAZV2FGgV8gOK8pLW26azi6H3VSpc9sVWM5jopLU3ILw3-lu5RbS3KM66fFuEZgB0gmVGa0AI-h8pdqZXcvv8m4kZycJaYjV" +
                "pbZUY3f1vWeE8UTZZPvITz0r95Ct4msuPK2Gr6vT5Jq5AMJuy94l3Ucb-0WzlptzMfsSEfSlgO_6qN6RJ9OciC2VmIEipfJK71" +
                "JSWmfV09Bl3rFa8JgfxxSWlBSLOIJnXxBDGt5RlUrjMoQLKv4695F0BTQm4Iwpt9w1yj9K_YGCK9vJtSgwmhwN9hiZNMkZCFBo" +
                "bhzH3RuqEUuhZSh81OoLYfVfsq-7Dd7PaQ3RES7T4VSwnJ6DtHqV2HJWwFrZ4snJu5Rqsv3fdd15deKcJVssAzp4djJbP1yqt0" +
                "bHiVfROcgU54ZIdqlgldGE_S2BF_31UppiQ2Zkfw5dEG8eKxIrQuwGKNU-twu5O7AcO_wv5Lv_yjQ";
        String testToken = String.format("%s %s", "Bearer", accessToken);
        Caller expectedCaller = Caller.builder()
                .username("hina.patel@pmjay")
                .isServiceAccount(false)
                .build();

        when(sessionServiceClient.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.just(true));
        when(consentManagerServiceProperties.getSuffix()).thenReturn("@pmjay");

        StepVerifier.create(authenticator.verify(testToken))
               .expectNext(expectedCaller)
                .verifyComplete();
    }
}