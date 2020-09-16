package in.org.projecteka.hiu.user.model;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class UserAuthOnConfirmResponse {
    UUID requestId;
    LocalDateTime timestamp;
    AuthorizationResponse auth;
    RespError error;
    @NotNull
    GatewayResponse resp;
}
