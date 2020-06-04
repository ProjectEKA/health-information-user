package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class HiuConsentNotificationRequest {
    @NotNull
    private UUID requestId;
    @NotNull
    private String timestamp;
    private ConsentNotification notification;
}
