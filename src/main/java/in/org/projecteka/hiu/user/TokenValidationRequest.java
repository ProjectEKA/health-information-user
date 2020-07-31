package in.org.projecteka.hiu.user;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenValidationRequest {
    String authToken;
}
