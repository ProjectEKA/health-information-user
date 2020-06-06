package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class HiuConsentNotificationRequest {
    @NotNull
    private UUID requestId;
    @NotNull
    private LocalDateTime timestamp;
    private ConsentNotification notification;
}
