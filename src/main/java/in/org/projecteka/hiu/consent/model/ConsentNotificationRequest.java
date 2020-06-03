package in.org.projecteka.hiu.consent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsentNotificationRequest {
    @NotNull
    private ConsentStatus status;
    @NotNull
    private LocalDateTime timestamp;
    private String consentRequestId;
    private List<ConsentArtefactReference> consentArtefacts;
}
