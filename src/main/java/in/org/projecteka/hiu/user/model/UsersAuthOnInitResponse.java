package in.org.projecteka.hiu.user.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@AllArgsConstructor
@Builder
public class UsersAuthOnInitResponse {
    UUID requestId;
    LocalDateTime timestamp;
    Auth auth;
    private RespError error;
    @NotNull
    private GatewayResponse resp;

}
