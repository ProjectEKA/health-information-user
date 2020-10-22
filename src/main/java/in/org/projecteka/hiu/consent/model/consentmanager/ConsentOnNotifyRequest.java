package in.org.projecteka.hiu.consent.model.consentmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.org.projecteka.hiu.common.GatewayResponse;
import in.org.projecteka.hiu.common.RespError;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Data
public class ConsentOnNotifyRequest {
    private UUID requestId;
    private LocalDateTime timestamp;
    private List<ConsentAcknowledgement> acknowledgement;
    private RespError error;
    @NonNull
    private GatewayResponse resp;
}
