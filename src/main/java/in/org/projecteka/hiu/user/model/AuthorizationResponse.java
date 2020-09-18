package in.org.projecteka.hiu.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor
@Builder
@Value
public class AuthorizationResponse {
    private String accessToken;
    Patient patient;
}
