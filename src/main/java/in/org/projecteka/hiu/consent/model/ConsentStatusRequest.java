package in.org.projecteka.hiu.consent.model;

import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class ConsentStatusRequest {
    UUID requestId;
    LocalDateTime timestamp;
    ConsentStatusDetail consentRequest;
    RespError error;
    @NotNull
    GatewayResponse resp;
}
